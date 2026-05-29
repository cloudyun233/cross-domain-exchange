/**
 * 订阅上下文 —— SSE + MQTT 双通道实时消息接收
 *
 * 架构设计：
 * - SSE 长连接：登录后立即建立，用于接收后端推送的 MQTT 消息，断线自动重连
 * - MQTT 连接：用户主动触发，后端代理连接 EMQX，cleanStart=false 支持离线消息补发
 * - 消息缓冲：保留最近 100 条消息，新消息在前
 *
 * SSE 重连策略：
 * - 指数退避：基础延迟 1500ms，每次重连延迟翻倍，最多重试 5 次
 * - 超过最大重试次数后提示用户刷新页面
 *
 * 生命周期：
 * - 登录 → 建立 SSE + 开始轮询会话状态
 * - 退出 → 关闭 SSE、清理所有状态
 */
import React, { ReactNode, createContext, useContext, useEffect, useRef, useState } from 'react';
import { EventSourcePolyfill } from 'event-source-polyfill';
import { message } from 'antd';
import { api } from '../services/api';
import { useAuth } from './AuthContext';

// ─── 常量 ────────────────────────────────────────────────────────────────────
/** SSE 最大重连次数，超过后停止重连并提示用户 */
const SSE_MAX_RECONNECT = 5;
/** SSE 重连基础延迟（毫秒），实际延迟 = SSE_BASE_DELAY_MS * 2^重试次数 */
const SSE_BASE_DELAY_MS = 1500;
/** 会话状态轮询间隔（毫秒） */
const STATUS_POLL_MS = 5000;

// ─── 类型 ────────────────────────────────────────────────────────────────────
export interface ReceivedMessage {
  topic: string;
  payload: string;
  timestamp: number;
  meta?: { source_format?: string; converter?: string };
}

type SseWaiter = {
  resolve: () => void;
  reject: (error: Error) => void;
  timer: ReturnType<typeof setTimeout>;
};

interface SubscribeContextType {
  topic: string;
  qos: number;
  activeTopic: string | null;
  messages: ReceivedMessage[];
  selectedKey: string[];
  selectedName: string;
  mqttConnected: boolean;
  mqttProtocol: string;
  sseConnected: boolean;
  subscribedTopics: string[];
  subscriptionCount: number;
  setTopic: (topic: string) => void;
  setQos: (qos: number) => void;
  setSelectedKey: (keys: string[]) => void;
  setSelectedName: (name: string) => void;
  /** 连接 MQTT（cleanStart=false，自动恢复已记忆订阅） */
  connectMqtt: () => Promise<void>;
  /** 仅断开 MQTT（SSE 保持，订阅记忆保持） */
  disconnectMqtt: () => Promise<void>;
  /** 订阅主题（MQTT 必须已连接） */
  subscribeTopic: () => Promise<void>;
  /** 取消订阅 */
  cancelTopic: (topic?: string) => Promise<void>;
  refreshSessionStatus: () => Promise<void>;
  clearMessages: () => void;
}

const SubscribeContext = createContext<SubscribeContextType | undefined>(undefined);

// ─── 持久化工具 ───────────────────────────────────────────────────────────────
const STORAGE_KEY = 'subscribe_state';

const loadFromStorage = () => {
  try {
    const s = sessionStorage.getItem(STORAGE_KEY);
    if (s) return JSON.parse(s) as { selectedKey?: string[]; selectedName?: string; qos?: number };
  } catch { /* ignore */ }
  return {};
};

const saveToStorage = (state: { selectedKey?: string[]; selectedName?: string; qos?: number }) => {
  try { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state)); } catch { /* ignore */ }
};

// ─── Provider ────────────────────────────────────────────────────────────────
export const SubscribeProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const saved = loadFromStorage();
  const { isAuthenticated } = useAuth();

  const [topic, setTopic] = useState('');
  const [qos, setQosState] = useState<number>(saved.qos ?? 1);
  const [activeTopic, setActiveTopic] = useState<string | null>(null);
  const [messages, setMessages] = useState<ReceivedMessage[]>([]);
  const [selectedKey, setSelectedKeyState] = useState<string[]>(saved.selectedKey || []);
  const [selectedName, setSelectedNameState] = useState<string>(saved.selectedName || '');
  const [mqttConnected, setMqttConnected] = useState(false);
  const [mqttProtocol, setMqttProtocol] = useState('未连接');
  const [sseConnected, setSseConnected] = useState(false);
  const [subscribedTopics, setSubscribedTopics] = useState<string[]>([]);
  const [subscriptionCount, setSubscriptionCount] = useState(0);

  // ── Refs ──────────────────────────────────────────────────────────────────
  const sseRef = useRef<EventSourcePolyfill | null>(null);
  const sseConnectedRef = useRef(false);
  const sseWaitersRef = useRef<SseWaiter[]>([]);
  const sseReconnTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const sseReconnAttemptsRef = useRef(0);
  const shouldSseReconnRef = useRef(false);
  const mqttConnectedRef = useRef(false);
  const isUnmountedRef = useRef(false);

  // ── 状态同步工具 ──────────────────────────────────────────────────────────

  const updateSseConnected = (connected: boolean) => {
    sseConnectedRef.current = connected;
    if (isUnmountedRef.current) return;
    setSseConnected(connected);
    if (connected) {
      const waiters = sseWaitersRef.current;
      sseWaitersRef.current = [];
      waiters.forEach((waiter) => {
        clearTimeout(waiter.timer);
        waiter.resolve();
      });
    }
  };

  const applySessionStatus = (data: any) => {
    if (isUnmountedRef.current) return;
    const connected = Boolean(data?.mqttConnected);
    mqttConnectedRef.current = connected;
    setMqttConnected(connected);
    setMqttProtocol(data?.protocol || '未连接');
    updateSseConnected(Boolean(data?.sseConnected));
    setSubscribedTopics(data?.subscribedTopics || []);
    setSubscriptionCount(Number(data?.subscriptionCount || 0));
  };

  const refreshSessionStatus = async () => {
    if (!isAuthenticated) { applySessionStatus(null); return; }
    try {
      const resp = await api.getSubscribeSessionStatus();
      if (resp.success) applySessionStatus(resp.data);
    } catch { applySessionStatus(null); }
  };

  /**
   * 推送消息到缓冲区：
   * - 尝试解析 JSON，若包含 _meta 字段则提取元数据（格式转换来源等）
   * - 解析失败则视为纯文本消息
   * - 保留最近 100 条，新消息在前
   */
  const pushMessage = (rawPayload: string, msgTopic: string, timestamp?: number) => {
    if (isUnmountedRef.current) return;
    let payloadText = rawPayload;
    let meta: ReceivedMessage['meta'];
    try {
      const parsed = JSON.parse(rawPayload);
      if (parsed._meta) { meta = parsed._meta; payloadText = JSON.stringify(parsed.data, null, 2); }
    } catch { /* plain text */ }
    setMessages((prev) =>
      [{ topic: msgTopic, payload: payloadText, timestamp: timestamp || Date.now(), meta }, ...prev].slice(0, 100)
    );
  };

  // ── SSE 持久长连接 ─────────────────────────────────────────────────────────

  const clearSseReconnTimer = () => {
    if (sseReconnTimerRef.current) { clearTimeout(sseReconnTimerRef.current); sseReconnTimerRef.current = null; }
  };

  const cancelSseWaiters = (reason: string) => {
    const waiters = sseWaitersRef.current;
    sseWaitersRef.current = [];
    waiters.forEach((waiter) => {
      clearTimeout(waiter.timer);
      waiter.reject(new Error(reason));
    });
  };

  /** 关闭 SSE 连接并清理重连定时器，将连接状态置为 false */
  const closeSse = (cancelWaiters = true) => {
    if (sseRef.current) { sseRef.current.close(); sseRef.current = null; }
    clearSseReconnTimer();

    if (cancelWaiters) {
      cancelSseWaiters('SSE 连接已关闭');
    }

    updateSseConnected(false);
  };

  /**
   * 建立 SSE 连接：
   * - 先关闭已有连接，防止重复连接
   * - 监听 connected/message/error 三类事件
   * - 连接成功后重置重连计数并刷新会话状态
   * - 连接断开时若 shouldSseReconn 为 true 则触发重连调度
   */
  const openSse = () => {
    closeSse(false);
    console.info('[SSE] 建立 SSE 连接...');
    const es = api.openSseChannel();
    sseRef.current = es;

    es.addEventListener('connected', ((event: any) => {
      if (event.data) console.info('[SSE] connected 事件收到:', event.data);
      sseReconnAttemptsRef.current = 0;
      updateSseConnected(true);
      void refreshSessionStatus();
    }) as any);

    es.addEventListener('message', ((event: any) => {
      try {
        const data = JSON.parse(event.data);
        console.info('[SSE] <<< 收到消息:', data.topic, 'payloadLen:', data.payload?.length);
        pushMessage(data.payload, data.topic, data.timestamp);
      } catch (err) { console.error('[SSE] 消息解析失败', err); }
    }) as any);

    es.addEventListener('error', ((event: any) => {
      if (!event.data) return;
      try {
        const d = JSON.parse(event.data);
        if (d?.message) message.error(d.message);
      } catch { /* ignore */ }
    }) as any);

    es.onerror = () => {
      console.warn('[SSE] 连接断开');
      updateSseConnected(false);
      if (shouldSseReconnRef.current) void scheduleSseReconn();
    };
  };

  /**
   * 确保 SSE 连接已建立，若未连接则发起连接并等待
   * - 使用 Promise + 等待队列模式，3 秒超时后拒绝
   * - 适用于 MQTT 连接/订阅等需要 SSE 通道就绪的前置操作
   */
  const ensureSseConnected = async () => {
    if (sseConnectedRef.current) return;

    if (!shouldSseReconnRef.current) {
      throw new Error('组件已卸载，无法建立 SSE 连接');
    }

    const connectedPromise = new Promise<void>((resolve, reject) => {
      let waiter: SseWaiter | null = null;
      const timer = setTimeout(() => {
        sseWaitersRef.current = sseWaitersRef.current.filter((item) => item !== waiter);
        reject(new Error('SSE 连接建立超时，请稍后重试'));
      }, 3000);
      waiter = {
        timer,
        reject,
        resolve: () => {
          clearTimeout(timer);
          if (!shouldSseReconnRef.current) {
            reject(new Error('组件已卸载，SSE 连接已取消'));
            return;
          }
          resolve();
        },
      };
      sseWaitersRef.current.push(waiter);
    });
    openSse();
    await connectedPromise;
  };

  /**
   * 调度 SSE 重连（指数退避）：
   * - 延迟 = SSE_BASE_DELAY_MS * 2^已重试次数
   * - 超过 SSE_MAX_RECONNECT 后停止重连并提示用户
   * - 每次重连前清理旧的重连定时器
   */
  const scheduleSseReconn = async () => {
    if (!shouldSseReconnRef.current) return;
    const attempts = sseReconnAttemptsRef.current;
    if (attempts >= SSE_MAX_RECONNECT) {
      shouldSseReconnRef.current = false;
      message.error('SSE 连接多次中断，请刷新页面重试');
      return;
    }
    const delay = SSE_BASE_DELAY_MS * Math.pow(2, attempts);
    console.warn(`[SSE] ${delay}ms 后重连 (第 ${attempts + 1} 次)`);
    clearSseReconnTimer();
    sseReconnTimerRef.current = setTimeout(() => {
      sseReconnAttemptsRef.current = attempts + 1;
      openSse();
    }, delay);
  };

  const enableSseReconnect = () => {
    shouldSseReconnRef.current = true;
    sseReconnAttemptsRef.current = 0;
    clearSseReconnTimer();
  };

  // ── 核心操作 ──────────────────────────────────────────────────────────────

  /**
   * 连接 MQTT。
   * 后端会自动恢复已记忆的订阅 + 推送 offline 消息。
   */
  const connectMqtt = async () => {
    console.info('[MQTT] 请求连接...');

    enableSseReconnect();
    await ensureSseConnected();

    const resp = await api.connectSubscribeSession();
    if (resp.success) {
      applySessionStatus(resp.data);
      console.info('[MQTT] 连接成功:', resp.data);
      message.success(resp.message || 'MQTT 已连接');
    } else {
      throw new Error(resp.message);
    }
  };

  /**
   * 仅断开 MQTT，SSE 保持，订阅记忆保持。
   */
  const disconnectMqtt = async () => {
    console.info('[MQTT] 请求断开...');
    const resp = await api.disconnectSubscribeSession();
    if (resp.success) {
      applySessionStatus(resp.data);
      console.info('[MQTT] 断开成功:', resp.data);
      message.info(resp.message || 'MQTT 已断开（离线消息将被缓存）');
    } else {
      throw new Error(resp.message);
    }
  };

  /**
   * 订阅主题（MQTT 必须已连接）。
   */
  const doSubscribeTopic = async () => {
    const nextTopic = topic.trim();
    if (!nextTopic) { message.warning('请选择或输入订阅主题'); return; }

    console.info('[Subscribe] 订阅主题:', nextTopic, 'qos:', qos);

    if (!mqttConnectedRef.current) {
      message.warning('请先连接 MQTT');
      return;
    }

    await ensureSseConnected();

    const resp = await api.subscribeToTopic(nextTopic, qos);
    if (resp.success) {
      applySessionStatus(resp.data);
      setActiveTopic(nextTopic);
      console.info('[Subscribe] 订阅成功:', resp.data);
      message.success(`已订阅: ${nextTopic}`);
    } else {
      message.error(resp.message || '订阅失败');
    }
  };

  /**
   * 取消订阅。
   */
  const doCancelTopic = async (targetTopic?: string) => {
    const current = targetTopic || activeTopic;
    if (!current) { message.info('无活跃订阅'); return; }

    console.info('[Subscribe] 取消订阅:', current);

    try {
      const resp = await api.cancelSubscribe(current);
      if (resp?.success) {
        applySessionStatus(resp.data);
      }
      if (activeTopic === current) setActiveTopic(null);
      message.info(`已取消订阅: ${current}`);
    } catch (err) {
      console.error('[Subscribe] 取消订阅失败', err);
      message.error((err as Error)?.message || `取消订阅失败: ${current}`);
      throw err;
    }
    await refreshSessionStatus();
  };

  // ── 生命周期 ──────────────────────────────────────────────────────────────

  /**
   * 认证状态变化时的生命周期管理：
   * - 登录：建立 SSE 持久连接 + 启动会话状态轮询
   * - 退出：关闭 SSE、清理重连标记、清空消息和订阅状态
   */
  useEffect(() => {
    isUnmountedRef.current = false;

    if (!isAuthenticated) {
      // 退出登录：关闭一切
      shouldSseReconnRef.current = false;
      closeSse();
      setActiveTopic(null);
      setMessages([]);
      applySessionStatus(null);
      return;
    }

    // 登录：建立持久 SSE + 开始轮询状态
    shouldSseReconnRef.current = true;
    sseReconnAttemptsRef.current = 0;
    openSse();

    void refreshSessionStatus();
    const timer = setInterval(() => void refreshSessionStatus(), STATUS_POLL_MS);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated]);

  // 组件卸载清理
  useEffect(() => {
    isUnmountedRef.current = false;
    return () => {
      isUnmountedRef.current = true;
      shouldSseReconnRef.current = false;
      closeSse();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    saveToStorage({ selectedKey, selectedName, qos });
  }, [selectedKey, selectedName, qos]);

  // ── 辅助 setter ──────────────────────────────────────────────────────────
  const setQos = (v: number) => setQosState(v);
  const setSelectedKey = (keys: string[]) => setSelectedKeyState(keys);
  const setSelectedName = (name: string) => setSelectedNameState(name);
  const clearMessages = () => setMessages([]);

  return (
    <SubscribeContext.Provider
      value={{
        topic, qos, activeTopic, messages,
        selectedKey, selectedName,
        mqttConnected, mqttProtocol, sseConnected,
        subscribedTopics, subscriptionCount,
        setTopic, setQos, setSelectedKey, setSelectedName,
        connectMqtt, disconnectMqtt,
        subscribeTopic: doSubscribeTopic,
        cancelTopic: doCancelTopic,
        refreshSessionStatus, clearMessages,
      }}
    >
      {children}
    </SubscribeContext.Provider>
  );
};

export const useSubscribe = () => {
  const ctx = useContext(SubscribeContext);
  if (!ctx) throw new Error('useSubscribe must be used within SubscribeProvider');
  return ctx;
};
