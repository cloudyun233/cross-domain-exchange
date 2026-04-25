import React, { ReactNode, createContext, useContext, useEffect, useRef, useState } from 'react';
import { EventSourcePolyfill } from 'event-source-polyfill';
import { message } from 'antd';
import { api } from '../services/api';
import { useAuth } from './AuthContext';

// ─── 常量 ────────────────────────────────────────────────────────────────────
const SSE_MAX_RECONNECT = 5;
const SSE_BASE_DELAY_MS = 1500;
const STATUS_POLL_MS = 5000;

// ─── 类型 ────────────────────────────────────────────────────────────────────
export interface ReceivedMessage {
  topic: string;
  payload: string;
  timestamp: number;
  meta?: { source_format?: string; converter?: string };
}

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
  cancelTopic: () => Promise<void>;
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
  const sseWaitersRef = useRef<Array<() => void>>([]);
  const sseReconnTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const sseReconnAttemptsRef = useRef(0);
  const shouldSseReconnRef = useRef(false);
  const mqttConnectedRef = useRef(false);

  // ── 状态同步工具 ──────────────────────────────────────────────────────────

  const updateSseConnected = (connected: boolean) => {
    sseConnectedRef.current = connected;
    setSseConnected(connected);
    if (connected) {
      const waiters = sseWaitersRef.current;
      sseWaitersRef.current = [];
      waiters.forEach((resolve) => resolve());
    }
  };

  const applySessionStatus = (data: any) => {
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

  const pushMessage = (rawPayload: string, msgTopic: string, timestamp?: number) => {
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

  const closeSse = () => {
    if (sseRef.current) { sseRef.current.close(); sseRef.current = null; }
    clearSseReconnTimer();
    updateSseConnected(false);
  };

  const openSse = () => {
    closeSse();
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

  const ensureSseConnected = async () => {
    if (sseConnectedRef.current) return;
    const connectedPromise = new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => {
        sseWaitersRef.current = sseWaitersRef.current.filter((waiter) => waiter !== onConnected);
        reject(new Error('SSE 连接建立超时，请稍后重试'));
      }, 3000);
      const onConnected = () => {
        clearTimeout(timer);
        resolve();
      };
      sseWaitersRef.current.push(onConnected);
    });
    openSse();
    await connectedPromise;
  };

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

  // ── 核心操作 ──────────────────────────────────────────────────────────────

  /**
   * 连接 MQTT。
   * 后端会自动恢复已记忆的订阅 + 推送 offline 消息。
   */
  const connectMqtt = async () => {
    console.info('[MQTT] 请求连接...');

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
  const doCancelTopic = async () => {
    const current = activeTopic;
    if (!current) { message.info('无活跃订阅'); return; }

    console.info('[Subscribe] 取消订阅:', current);

    try {
      await api.cancelSubscribe(current);
      setActiveTopic(null);
      message.info(`已取消订阅: ${current}`);
    } catch (err) {
      console.error('[Subscribe] 取消订阅失败', err);
    }
    await refreshSessionStatus();
  };

  // ── 生命周期 ──────────────────────────────────────────────────────────────

  useEffect(() => {
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
  useEffect(() => () => {
    shouldSseReconnRef.current = false;
    closeSse();
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
