import React, { useState, useRef, useEffect } from 'react';
import { EventSourcePolyfill } from 'event-source-polyfill';
import { Card, Input, Button, Select, Typography, Tag, List, Space, message, Badge, Empty } from 'antd';
import { PlayCircleOutlined, PauseCircleOutlined } from '@ant-design/icons';
import { api } from '../services/api';

const { Title, Text } = Typography;

interface ReceivedMessage {
  topic: string;
  payload: string;
  timestamp: number;
  meta?: { source_format?: string; converter?: string };
}

const Subscribe: React.FC = () => {
  const [topic, setTopic] = useState('/cross_domain/medical/#');
  const [qos, setQos] = useState(1);
  const [listening, setListening] = useState(false);
  const [messages, setMessages] = useState<ReceivedMessage[]>([]);
  const esRef = useRef<EventSourcePolyfill | null>(null);
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const reconnectAttemptsRef = useRef<number>(0);
  const maxReconnectAttempts = 5;
  const baseReconnectDelay = 1000; // 1秒

  const startListening = () => {
    if (!topic) { message.warning('请输入订阅主题'); return; }

    // 清理旧连接
    if (esRef.current) {
      esRef.current.close();
      esRef.current = null;
    }

    const es = api.createSubscribeStream(topic, qos);
    esRef.current = es;
    reconnectAttemptsRef.current = 0;

    es.addEventListener('connected', (() => {
      message.success('SSE连接已建立, 等待消息...');
      reconnectAttemptsRef.current = 0; // 重置重连计数器
    }) as any);

    es.addEventListener('message', ((e: any) => {
      try {
        const data = JSON.parse(e.data);
        let meta = undefined;
        let payloadStr = data.payload;
        // 检查是否有转换标记
        try {
          const parsed = JSON.parse(data.payload);
          if (parsed._meta) {
            meta = parsed._meta;
            payloadStr = JSON.stringify(parsed.data, null, 2);
          }
        } catch { /* not json, use as-is */ }

        setMessages(prev => [{
          topic: data.topic,
          payload: payloadStr,
          timestamp: data.timestamp || Date.now(),
          meta,
        }, ...prev].slice(0, 100));
      } catch (err) {
        console.error('解析消息失败', err);
      }
    }) as any);

    es.addEventListener('error', (e: any) => {
      const errData = e.data ? JSON.parse(e.data) : null;
      if (errData?.message) {
        message.error(errData.message);
      }
    });

    es.onerror = () => {
      if (listening) {
        handleReconnect();
      }
    };

    setListening(true);
  };

  const handleReconnect = () => {
    if (!listening) return;

    const attempts = reconnectAttemptsRef.current;
    if (attempts >= maxReconnectAttempts) {
      message.error('SSE连接失败，已达到最大重连次数，请手动重新连接');
      setListening(false);
      return;
    }

    // 指数退避重连
    const delay = baseReconnectDelay * Math.pow(2, attempts);
    message.warning(`SSE连接中断，${Math.round(delay / 1000)}秒后尝试重连...`);

    // 清理之前的定时器
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
    }

    // 设置新的重连定时器
    reconnectTimeoutRef.current = setTimeout(() => {
      // 先清理旧连接
      if (esRef.current) {
        esRef.current.close();
        esRef.current = null;
      }
      reconnectAttemptsRef.current = attempts + 1;
      message.info(`尝试重连 (${reconnectAttemptsRef.current}/${maxReconnectAttempts})...`);
      startListening();
    }, delay);
  };

  const stopListening = () => {
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }
    if (esRef.current) {
      esRef.current.close();
      esRef.current = null;
    }
    setListening(false);
    message.info('已停止监听');
  };

  // 组件卸载时清理资源
  useEffect(() => {
    return () => {
      stopListening();
    };
  }, []);

  const presetTopics = [
    { value: '/cross_domain/medical/#', label: '医疗域 (所有)' },
    { value: '/cross_domain/medical/hosp_swu/#', label: '西南医院' },
    { value: '/cross_domain/gov/#', label: '政务域 (所有)' },
    { value: '/cross_domain/#', label: '全域 (管理员)' },
  ];

  return (
    <div>
      <Title level={4}>数据订阅（消费者视角）</Title>

      <Card style={{ marginBottom: 16 }}>
        <Space wrap size="middle" style={{ width: '100%' }}>
          <div>
            <Text strong>订阅主题：</Text>
            <Select
              style={{ width: 300, marginLeft: 8 }}
              value={topic}
              onChange={setTopic}
              options={presetTopics}
              showSearch
              allowClear
              placeholder="选择或输入主题 (支持通配符)"
              dropdownRender={(menu) => (
                <>
                  {menu}
                  <div style={{ padding: '4px 8px' }}>
                    <Input
                      size="small"
                      placeholder="自定义主题..."
                      onPressEnter={(e: any) => setTopic(e.target.value)}
                    />
                  </div>
                </>
              )}
            />
          </div>
          <div>
            <Text strong>QoS：</Text>
            <Select
              style={{ width: 160, marginLeft: 8 }}
              value={qos}
              onChange={setQos}
              options={[
                { value: 0, label: 'QoS 0 - 最多一次' },
                { value: 1, label: 'QoS 1 - 至少一次' },
                { value: 2, label: 'QoS 2 - 精确一次' },
              ]}
            />
          </div>
          {!listening ? (
            <Button type="primary" icon={<PlayCircleOutlined />} onClick={startListening}>
              开始监听
            </Button>
          ) : (
            <Button danger icon={<PauseCircleOutlined />} onClick={stopListening}>
              停止监听
            </Button>
          )}
          <Badge
            status={listening ? 'processing' : 'default'}
            text={listening ? '监听中...' : '未监听'}
          />
        </Space>
      </Card>

      <Card
        title={<Space>接收到的消息 <Tag>{messages.length}条</Tag></Space>}
        extra={messages.length > 0 && <Button size="small" onClick={() => setMessages([])}>清空</Button>}
      >
        {messages.length === 0 ? (
          <Empty description={listening ? '等待消息中...' : '点击"开始监听"接收消息'} />
        ) : (
          <List
            dataSource={messages}
            renderItem={(msg) => (
              <List.Item>
                <div style={{ width: '100%' }}>
                  <Space style={{ marginBottom: 4 }}>
                    <Tag color="blue">{msg.topic}</Tag>
                    <Text type="secondary">{new Date(msg.timestamp).toLocaleString()}</Text>
                    {msg.meta?.converter && (
                      <Tag color="orange">Source: {msg.meta.converter}</Tag>
                    )}
                  </Space>
                  <pre style={{
                    background: '#f5f5f5', padding: 8, borderRadius: 4,
                    fontSize: 12, maxHeight: 200, overflow: 'auto', margin: 0,
                  }}>
                    {msg.payload}
                  </pre>
                </div>
              </List.Item>
            )}
          />
        )}
      </Card>
    </div>
  );
};

export default Subscribe;
