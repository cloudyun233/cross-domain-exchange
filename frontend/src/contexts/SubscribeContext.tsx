import React, { ReactNode, createContext, useContext, useEffect, useRef, useState } from 'react';
import { EventSourcePolyfill } from 'event-source-polyfill';
import { message } from 'antd';
import { api } from '../services/api';
import { useAuth } from './AuthContext';

const MAX_RECONNECT_ATTEMPTS = 5;
const BASE_RECONNECT_DELAY = 1000;
const STATUS_POLL_INTERVAL = 5000;

export interface ReceivedMessage {
  topic: string;
  payload: string;
  timestamp: number;
  meta?: { source_format?: string; converter?: string };
}

interface StopListeningOptions {
  silent?: boolean;
  cancelRemote?: boolean;
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
  startListening: () => Promise<void>;
  stopListening: (options?: StopListeningOptions) => Promise<void>;
  connectMqtt: () => Promise<void>;
  disconnectMqtt: () => Promise<void>;
  refreshSessionStatus: () => Promise<void>;
  clearMessages: () => void;
}

const SubscribeContext = createContext<SubscribeContextType | undefined>(undefined);
const STORAGE_KEY = 'subscribe_state';

const loadFromStorage = (): { selectedKey?: string[]; selectedName?: string; qos?: number } => {
  try {
    const saved = sessionStorage.getItem(STORAGE_KEY);
    if (saved) {
      return JSON.parse(saved);
    }
  } catch {
    // ignore parse errors
  }
  return {};
};

const saveToStorage = (state: { selectedKey?: string[]; selectedName?: string; qos?: number }) => {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // ignore storage errors
  }
};

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

  const esRef = useRef<EventSourcePolyfill | null>(null);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reconnectAttemptsRef = useRef(0);
  const shouldReconnectRef = useRef(false);
  const activeTopicRef = useRef<string | null>(null);
  const activeQosRef = useRef(1);

  const applySessionStatus = (data: any) => {
    setMqttConnected(Boolean(data?.mqttConnected));
    setMqttProtocol(data?.protocol || '未连接');
    setSseConnected(Boolean(data?.sseConnected));
    setSubscriptionCount(Number(data?.subscriptionCount || 0));
  };

  const clearReconnectTimer = () => {
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }
  };

  const closeStream = () => {
    if (esRef.current) {
      esRef.current.close();
      esRef.current = null;
    }
  };

  const resetListeningState = () => {
    activeTopicRef.current = null;
    setActiveTopic(null);
    setListening(false);
  };

  const pushMessage = (rawPayload: string, messageTopic: string, timestamp?: number) => {
    let payloadText = rawPayload;
    let meta: ReceivedMessage['meta'];

    try {
      const parsed = JSON.parse(rawPayload);
      if (parsed._meta) {
        meta = parsed._meta;
        payloadText = JSON.stringify(parsed.data, null, 2);
      }
    } catch {
      // plain text payload
    }

    setMessages((prev) => [{
      topic: messageTopic,
      payload: payloadText,
      timestamp: timestamp || Date.now(),
      meta,
    }, ...prev].slice(0, 100));
  };

  const refreshSessionStatus = async () => {
    if (!isAuthenticated) {
      applySessionStatus(null);
      return;
    }

    try {
      const resp = await api.getSubscribeSessionStatus();
      if (resp.success) {
        applySessionStatus(resp.data);
      }
    } catch {
      applySessionStatus(null);
    }
  };

  const handleTerminalError = async (errorMessage: string) => {
    shouldReconnectRef.current = false;
    clearReconnectTimer();
    closeStream();
    resetListeningState();
    setSseConnected(false);
    await refreshSessionStatus();
    message.error(errorMessage);
  };

  const createStream = (nextTopic: string, nextQos: number) => {
    closeStream();

    const es = api.createSubscribeStream(nextTopic, nextQos);
    esRef.current = es;

    es.addEventListener('connected', ((event: any) => {
      if (event.data) {
        message.success('订阅连接已建立，等待消息...');
      }
      reconnectAttemptsRef.current = 0;
      setListening(true);
      setSseConnected(true);
      void refreshSessionStatus();
    }) as any);

    es.addEventListener('message', ((event: any) => {
      try {
        const data = JSON.parse(event.data);
        pushMessage(data.payload, data.topic, data.timestamp);
      } catch (error) {
        console.error('Failed to parse subscription message', error);
      }
    }) as any);

    es.addEventListener('error', ((event: any) => {
      if (!event.data) {
        return;
      }

      try {
        const errorData = JSON.parse(event.data);
        if (errorData?.reconnectable === false) {
          void handleTerminalError(errorData.message || '订阅失败');
          return;
        }
        if (errorData?.message) {
          message.error(errorData.message);
        }
      } catch {
        // ignore malformed payload
      }
    }) as any);

    es.onerror = () => {
      if (shouldReconnectRef.current) {
        void handleReconnect();
      }
    };
  };

  const handleReconnect = async () => {
    if (!shouldReconnectRef.current) {
      return;
    }

    const attempts = reconnectAttemptsRef.current;
    if (attempts >= MAX_RECONNECT_ATTEMPTS) {
      shouldReconnectRef.current = false;
      setListening(false);
      message.error('SSE 重连失败，已达到最大次数，请手动重试');
      return;
    }

    const retryTopic = activeTopicRef.current;
    if (!retryTopic) {
      shouldReconnectRef.current = false;
      setListening(false);
      return;
    }

    const delay = BASE_RECONNECT_DELAY * Math.pow(2, attempts);
    message.warning(`SSE 连接中断，${Math.round(delay / 1000)} 秒后尝试重连`);

    clearReconnectTimer();
    reconnectTimeoutRef.current = setTimeout(() => {
      reconnectAttemptsRef.current = attempts + 1;
      createStream(retryTopic, activeQosRef.current);
    }, delay);
  };

  const stopListening = async ({ silent = false, cancelRemote = true }: StopListeningOptions = {}) => {
    shouldReconnectRef.current = false;
    clearReconnectTimer();
    closeStream();

    const currentTopic = activeTopicRef.current;
    resetListeningState();
    setSseConnected(false);

    if (cancelRemote && currentTopic) {
      try {
        await api.cancelSubscribe(currentTopic);
      } catch (error) {
        console.error('Failed to cancel subscription', error);
      }
    }

    await refreshSessionStatus();

    if (!silent) {
      message.info('已停止监听');
    }
  };

  const startListening = async () => {
    const nextTopic = topic.trim();
    if (!nextTopic) {
      message.warning('请选择或输入订阅主题');
      return;
    }

    if (activeTopicRef.current) {
      await stopListening({ silent: true });
    }

    shouldReconnectRef.current = true;
    activeTopicRef.current = nextTopic;
    activeQosRef.current = qos;
    setActiveTopic(nextTopic);
    setListening(true);
    createStream(nextTopic, qos);
  };

  const connectMqtt = async () => {
    const resp = await api.connectSubscribeSession();
    if (resp.success) {
      applySessionStatus(resp.data);
      message.success(resp.message || 'MQTT 已连接');
    } else {
      throw new Error(resp.message);
    }
  };

  const disconnectMqtt = async () => {
    const resp = await api.disconnectSubscribeSession();
    if (resp.success) {
      applySessionStatus(resp.data);
      message.info(resp.message || 'MQTT 已断开');
    } else {
      throw new Error(resp.message);
    }
  };

  const clearMessages = () => {
    setMessages([]);
  };

  const setQos = (newQos: number) => {
    setQosState(newQos);
  };

  const setSelectedKey = (keys: string[]) => {
    setSelectedKeyState(keys);
  };

  const setSelectedName = (name: string) => {
    setSelectedNameState(name);
  };

  useEffect(() => {
    saveToStorage({ selectedKey, selectedName, qos });
  }, [selectedKey, selectedName, qos]);

  useEffect(() => {
    if (!isAuthenticated) {
      if (activeTopicRef.current) {
        void stopListening({ silent: true, cancelRemote: false });
      }
      clearMessages();
      applySessionStatus(null);
      return;
    }

    void refreshSessionStatus();
    const timer = setInterval(() => {
      void refreshSessionStatus();
    }, STATUS_POLL_INTERVAL);

    return () => clearInterval(timer);
  }, [isAuthenticated]);

  useEffect(() => () => {
    shouldReconnectRef.current = false;
    clearReconnectTimer();
    closeStream();
  }, []);

  return (
    <SubscribeContext.Provider
      value={{
        topic,
        qos,
        listening,
        activeTopic,
        messages,
        selectedKey,
        selectedName,
        mqttConnected,
        mqttProtocol,
        sseConnected,
        subscriptionCount,
        setTopic,
        setQos,
        setSelectedKey,
        setSelectedName,
        startListening,
        stopListening,
        connectMqtt,
        disconnectMqtt,
        refreshSessionStatus,
        clearMessages,
      }}
    >
      {children}
    </SubscribeContext.Provider>
  );
};

export const useSubscribe = () => {
  const context = useContext(SubscribeContext);
  if (!context) {
    throw new Error('useSubscribe must be used within SubscribeProvider');
  }
  return context;
};
