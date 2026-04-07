import React, { ReactNode, createContext, useContext, useState } from 'react';

interface DomainTreeNode {
  key: string;
  title: string;
  domainCode?: string;
  pathName?: string;
  topicPath?: string;
  subscribeTopic?: string;
  isLeaf?: boolean;
  children?: DomainTreeNode[];
}

export interface PublishHistoryItem {
  topic: string;
  qos: number;
  format: string;
  time: string;
  success: boolean;
  error?: string;
}

interface PublishContextType {
  selectedTopic: string;
  selectedNode: DomainTreeNode | null;
  qos: number;
  format: 'structured' | 'text';
  payload: string;
  history: PublishHistoryItem[];
  setSelectedTopic: (topic: string) => void;
  setSelectedNode: (node: DomainTreeNode | null) => void;
  setQos: (qos: number) => void;
  setFormat: (format: 'structured' | 'text') => void;
  setPayload: (payload: string) => void;
  addHistory: (item: PublishHistoryItem) => void;
  clearHistory: () => void;
}

const PublishContext = createContext<PublishContextType | undefined>(undefined);

const STORAGE_KEY = 'publish_state';

const loadFromStorage = (): Partial<PublishContextType> => {
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

const saveToStorage = (state: Partial<PublishContextType>) => {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // ignore storage errors
  }
};

const STRUCTURED_SAMPLE = `{
  "patientId": "P20260401001",
  "name": "张三",
  "diagnosis": "常规体检",
  "timestamp": "2026-04-01T08:00:00"
}`;

const PLAIN_TEXT_SAMPLE = '普通文本消息：西南医院已完成病历脱敏';

export const PublishProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const saved = loadFromStorage();

  const [selectedTopic, setSelectedTopic] = useState<string>(saved.selectedTopic || '');
  const [selectedNode, setSelectedNode] = useState<DomainTreeNode | null>(saved.selectedNode || null);
  const [qos, setQos] = useState<number>(saved.qos ?? 1);
  const [format, setFormatState] = useState<'structured' | 'text'>(saved.format || 'structured');
  const [payload, setPayloadState] = useState<string>(saved.payload || STRUCTURED_SAMPLE);
  const [history, setHistory] = useState<PublishHistoryItem[]>(saved.history || []);

  const persistState = (updates: Partial<PublishContextType>) => {
    const current = loadFromStorage();
    saveToStorage({ ...current, ...updates });
  };

  const handleSetSelectedTopic = (topic: string) => {
    setSelectedTopic(topic);
    persistState({ selectedTopic: topic });
  };

  const handleSetSelectedNode = (node: DomainTreeNode | null) => {
    setSelectedNode(node);
    persistState({ selectedNode: node });
  };

  const handleSetQos = (newQos: number) => {
    setQos(newQos);
    persistState({ qos: newQos });
  };

  const handleSetFormat = (newFormat: 'structured' | 'text') => {
    setFormatState(newFormat);
    const newPayload = newFormat === 'text' ? PLAIN_TEXT_SAMPLE : STRUCTURED_SAMPLE;
    setPayloadState(newPayload);
    persistState({ format: newFormat, payload: newPayload });
  };

  const handleSetPayload = (newPayload: string) => {
    setPayloadState(newPayload);
    persistState({ payload: newPayload });
  };

  const addHistory = (item: PublishHistoryItem) => {
    setHistory((prev) => {
      const newHistory = [item, ...prev.slice(0, 19)];
      persistState({ history: newHistory });
      return newHistory;
    });
  };

  const clearHistory = () => {
    setHistory([]);
    persistState({ history: [] });
  };

  return (
    <PublishContext.Provider
      value={{
        selectedTopic,
        selectedNode,
        qos,
        format,
        payload,
        history,
        setSelectedTopic: handleSetSelectedTopic,
        setSelectedNode: handleSetSelectedNode,
        setQos: handleSetQos,
        setFormat: handleSetFormat,
        setPayload: handleSetPayload,
        addHistory,
        clearHistory,
      }}
    >
      {children}
    </PublishContext.Provider>
  );
};

export const usePublish = () => {
  const context = useContext(PublishContext);
  if (!context) {
    throw new Error('usePublish must be used within PublishProvider');
  }
  return context;
};
