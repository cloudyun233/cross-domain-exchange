import React from 'react';
import { Badge, Button, Card, Empty, Input, List, Select, Space, Tag, Typography } from 'antd';
import { PauseCircleOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { useSubscribe } from '../contexts/SubscribeContext';

const { Title, Text } = Typography;

const Subscribe: React.FC = () => {
  const {
    topic,
    qos,
    listening,
    activeTopic,
    messages,
    setTopic,
    setQos,
    startListening,
    stopListening,
    clearMessages,
  } = useSubscribe();

  const presetTopics = [
    { value: '/cross_domain/medical/#', label: '医疗域(全部)' },
    { value: '/cross_domain/medical/hosp_swu/#', label: '西南医院' },
    { value: '/cross_domain/gov/#', label: '政务域(全部)' },
    { value: '/cross_domain/#', label: '全域(管理员)' },
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
              placeholder="选择或输入主题(支持通配符)"
              dropdownRender={(menu) => (
                <>
                  {menu}
                  <div style={{ padding: '4px 8px' }}>
                    <Input
                      size="small"
                      placeholder="自定义主题..."
                      onPressEnter={(event: React.KeyboardEvent<HTMLInputElement>) => {
                        setTopic(event.currentTarget.value);
                      }}
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
            <Button type="primary" icon={<PlayCircleOutlined />} onClick={() => void startListening()}>
              开始监听
            </Button>
          ) : (
            <Button danger icon={<PauseCircleOutlined />} onClick={() => void stopListening()}>
              停止监听
            </Button>
          )}
          <Badge
            status={listening ? 'processing' : 'default'}
            text={listening ? `监听中: ${activeTopic || topic}` : '未监听'}
          />
        </Space>
      </Card>

      <Card
        title={<Space>接收到的消息 <Tag>{messages.length}条</Tag></Space>}
        extra={messages.length > 0 && <Button size="small" onClick={clearMessages}>清空</Button>}
      >
        {messages.length === 0 ? (
          <Empty description={listening ? '等待消息中...' : '点击“开始监听”接收消息'} />
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
                    background: '#f5f5f5',
                    padding: 8,
                    borderRadius: 4,
                    fontSize: 12,
                    maxHeight: 200,
                    overflow: 'auto',
                    margin: 0,
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
