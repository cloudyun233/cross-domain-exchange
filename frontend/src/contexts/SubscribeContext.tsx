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
  listening: boolean;
  activeTopic: string | null;
  messages: ReceivedMessage[];
  selectedKey: string[];
  selectedName: string;
  mqttConnected: boolean;
  mqttProtocol: string;
  sseConnected: boolean;
  subscriptionCount: number;
  setTopic: (topic: string) => void;
  setQos: (qos: number) => void;
  setSelectedKey: (keys: string[]) => void;
  setSelectedName: (name: string) => void;
  /**
   * 开始监听主题：
   * - 若 MQTT 未连接，先连接再订阅
   * - 若 MQTT 已连接，直接订阅
   */
  startListening: () => Promise<void>;
  /** 取消当前主题订阅（保持 SSE 和 MQTT 连接） */
  stopListening: () => Promise<void>;
  /** 连接 MQTT（cleanStart=false，EMQX 推送 offline 消息） */
  connectMqtt: () => Promise<void>;
  /** 仅断开 MQTT，SSE 保持，EMQX 开始缓存离线消息 */
  disconnectMqtt: () => Promise<void>;
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
  const [listening, setListening] = useState(false);
  const [activeTopic, setActiveTopic] = useState<string | null>(null);
  const [messages, setMessages] = useState<ReceivedMessage[]>([]);
  const [selectedKey, setSelectedKeyState] = useState<string[]>(saved.selectedKey || []);
  const [selectedName, setSelectedNameState] = useState<string>(saved.selectedName || '');
  const [mqttConnected, setMqttConnected] = useState(false);
  const [mqttProtocol, setMqttProtocol] = useState('未连接');
  const [sseConnected, setSseConnected] = useState(false);
  const [subscriptionCount, setSubscriptionCount] = useState(0);

  // ── Refs ──────────────────────────────────────────────────────────────────
  /** 持久 SSE 长连接（登录期间保持，不随 MQTT 状态变化） */
  const sseRef = useRef<EventSourcePolyfill | null>(null);
  const sseReconnTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const sseReconnAttemptsRef = useRef(0);
  const shouldSseReconnRef = useRef(false);

  /** 当前活跃主题 ref（避免闭包陷阱） */
  const activeTopicRef = useRef<string | null>(null);
  const activeQosRef = useRef(1);
  /** 当前 MQTT 连接状态 ref（避免在异步操作中读取过时状态） */
  const mqttConnectedRef = useRef(false);

  // ── 状态同步工具 ──────────────────────────────────────────────────────────

  const applySessionStatus = (data: any) => {
    const connected = Boolean(data?.mqttConnected);
    mqttConnectedRef.current = connected;
    setMqttConnected(connected);
    setMqttProtocol(data?.protocol || '未连接');
    setSseConnected(Boolean(data?.sseConnected));
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
    setSseConnected(false);
  };

  const openSse = () => {
    closeSse();
    const es = api.openSseChannel();
    sseRef.current = es;

    es.addEventListener('connected', ((event: any) => {
      if (event.data) console.info('[SSE] channel established');
      sseReconnAttemptsRef.current = 0;
      setSseConnected(true);
      void refreshSessionStatus();
    }) as any);

    es.addEventListener('message', ((event: any) => {
      try {
        const data = JSON.parse(event.data);
        pushMessage(data.payload, data.topic, data.timestamp);
      } catch (err) { console.error('[SSE] parse error', err); }
    }) as any);

    es.addEventListener('error', ((event: any) => {
      if (!event.data) return;
      try {
        const d = JSON.parse(event.data);
        if (d?.message) message.error(d.message);
      } catch { /* ignore */ }
    }) as any);

    es.onerror = () => {
      setSseConnected(false);
      if (shouldSseReconnRef.current) void scheduleSseReconn();
    };
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
    console.warn(`[SSE] reconnect in ${delay}ms (attempt ${attempts + 1})`);
    clearSseReconnTimer();
    sseReconnTimerRef.current = setTimeout(() => {
      sseReconnAttemptsRef.current = attempts + 1;
      openSse();
    }, delay);
  };

  // ── 核心操作 ──────────────────────────────────────────────────────────────

  /**
   * 开始监听主题。
   *
   * 流程：
   * 1. 确保 SSE 已建立（若无则重建）
   * 2. 若 MQTT 未连接 → 先调 connectSession（EMQX 推 offline 消息）
   * 3. 向 EMQX 订阅主题（POST /subscribe/topic）
   * 4. 更新 UI 状态
   *
   * 这样既能收实时消息，重连后也能收到 offline 消息。
   */
  const startListening = async () => {
    const nextTopic = topic.trim();
    if (!nextTopic) { message.warning('请选择或输入订阅主题'); return; }

    // 1. 确保 SSE 通道存在
    if (!sseRef.current) {
      openSse();
      // 稍等 SSE 握手完成（避免消息丢失）
      await new Promise((resolve) => setTimeout(resolve, 600));
    }

    try {
      // 2. 若 MQTT 未连接，先连接（会触发 offline 消息补发）
      if (!mqttConnectedRef.current) {
        const connectResp = await api.connectSubscribeSession();
        if (!connectResp.success) throw new Error(connectResp.message);
        applySessionStatus(connectResp.data);
      }

      // 3. 订阅主题（向 EMQX 发 SUBSCRIBE 报文）
      const subResp = await api.subscribeToTopic(nextTopic, qos);
      if (!subResp.success) throw new Error(subResp.message);
      applySessionStatus(subResp.data);

      activeTopicRef.current = nextTopic;
      activeQosRef.current = qos;
      setActiveTopic(nextTopic);
      setListening(true);
      message.success(`已订阅: ${nextTopic}`);
    } catch (err: any) {
      message.error(err.message || '订阅失败');
    }
  };

  /** 停止监听（取消 EMQX 订阅，保持 MQTT 和 SSE 连接） */
  const stopListening = async () => {
    const current = activeTopicRef.current;
    activeTopicRef.current = null;
    setActiveTopic(null);
    setListening(false);

    if (current) {
      try { await api.cancelSubscribe(current); } catch (err) { console.error('[unsubscribe]', err); }
    }
    await refreshSessionStatus();
    message.info('已停止监听');
  };

  /**
   * 连接 MQTT（cleanStart=false，持久会话）。
   * EMQX 将自动补发该 clientId 的所有 offline 消息。
   * 必须在 SSE 已建立后调用，否则 offline 消息推回时无处接收。
   */
  const connectMqtt = async () => {
    if (!sseRef.current || !sseConnected) {
      message.warning('SSE 未就绪，正在重建 SSE 通道...');
      openSse();
      await new Promise((resolve) => setTimeout(resolve, 600));
    }
    const resp = await api.connectSubscribeSession();
    if (resp.success) {
      applySessionStatus(resp.data);
      message.success(resp.message || 'MQTT 已连接，正在接收离线消息...');
    } else {
      throw new Error(resp.message);
    }
  };

  /** 仅断开 MQTT，SSE 保持长连接，EMQX 开始缓存 offline 消息 */
  const disconnectMqtt = async () => {
    const resp = await api.disconnectSubscribeSession();
    if (resp.success) {
      applySessionStatus(resp.data);
      message.info(resp.message || 'MQTT 已断开（SSE 保持，离线消息将被缓存）');
    } else {
      throw new Error(resp.message);
    }
  };

  // ── 生命周期 ──────────────────────────────────────────────────────────────

  useEffect(() => {
    if (!isAuthenticated) {
      // 退出登录：关闭一切
      shouldSseReconnRef.current = false;
      closeSse();
      activeTopicRef.current = null;
      setActiveTopic(null);
      setListening(false);
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
        topic, qos, listening, activeTopic, messages,
        selectedKey, selectedName,
        mqttConnected, mqttProtocol, sseConnected, subscriptionCount,
        setTopic, setQos, setSelectedKey, setSelectedName,
        startListening, stopListening,
        connectMqtt, disconnectMqtt,
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
