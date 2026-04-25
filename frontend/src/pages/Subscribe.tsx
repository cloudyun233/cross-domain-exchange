import React, { useEffect, useState } from 'react';
import { Badge, Button, Card, Col, Empty, Input, List, Radio, Row, Space, Tag, Tree, Typography, message } from 'antd';
import { DisconnectOutlined, LinkOutlined, MinusCircleOutlined, PlusCircleOutlined } from '@ant-design/icons';
import { useSubscribe } from '../contexts/SubscribeContext';
import { api } from '../services/api';

const { Title, Text } = Typography;

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

const Subscribe: React.FC = () => {
  const [domainTree, setDomainTree] = useState<DomainTreeNode[]>([]);
  const [actionLoading, setActionLoading] = useState(false);
  const {
    topic,
    qos,
    activeTopic,
    messages,
    selectedKey,
    selectedName,
    mqttConnected,
    mqttProtocol,
    sseConnected,
    subscribedTopics,
    subscriptionCount,
    setTopic,
    setQos,
    setSelectedKey,
    setSelectedName,
    connectMqtt,
    disconnectMqtt,
    subscribeTopic,
    cancelTopic,
    clearMessages,
  } = useSubscribe();

  useEffect(() => {
    api.getDomainTree().then((resp) => {
      if (resp.success) {
        setDomainTree(resp.data);
      }
    });
  }, []);

  const handleConnect = async () => {
    setActionLoading(true);
    try {
      await connectMqtt();
    } catch (error: any) {
      message.error(error.message || 'MQTT 连接失败');
    } finally {
      setActionLoading(false);
    }
  };

  const handleDisconnect = async () => {
    setActionLoading(true);
    try {
      await disconnectMqtt();
    } catch (error: any) {
      message.error(error.message || 'MQTT 断开失败');
    } finally {
      setActionLoading(false);
    }
  };

  const handleSubscribe = async () => {
    setActionLoading(true);
    try {
      await subscribeTopic();
    } catch (error: any) {
      message.error(error.message || '订阅失败');
    } finally {
      setActionLoading(false);
    }
  };

  const handleCancel = async () => {
    setActionLoading(true);
    try {
      await cancelTopic();
    } catch (error: any) {
      message.error(error.message || '取消订阅失败');
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="page-stack">
      <div className="page-hero">
        <div>
          <Title level={3} className="page-title">数据订阅</Title>
          <Text className="page-subtitle">连接 MQTT 服务后按安全域订阅主题，实时查看跨域消息。</Text>
        </div>
        <Tag color={mqttConnected ? 'success' : 'default'}>{mqttConnected ? '已连接' : '未连接'}</Tag>
      </div>
      <Row gutter={[18, 18]}>
        <Col xs={24} lg={9}>
          <Card title="选择订阅域" extra={<Tag color="blue">后端域表驱动</Tag>} style={{ marginBottom: 16 }}>
            <div className="ios-group" style={{ padding: 8 }}>
              <Tree
              showLine
              blockNode
              defaultExpandAll
              treeData={domainTree as any}
              selectedKeys={selectedKey}
              onSelect={(keys, info) => {
                const node = info.node as any;
                const subscribeTopic = node.subscribeTopic || `${node.key}/#`;
                setSelectedKey(keys as string[]);
                setSelectedName(node.pathName || node.title || '');
                setTopic(subscribeTopic);
              }}
              titleRender={(nodeData: any) => (
                <Space size="small">
                  <span>{nodeData.title}</span>
                  {nodeData.domainCode && <Tag color={nodeData.isLeaf ? 'green' : 'cyan'}>{nodeData.domainCode}</Tag>}
                </Space>
              )}
              style={{ maxHeight: 420, overflow: 'auto' }}
              />
            </div>
            <Space direction="vertical" size={6} style={{ marginTop: 12, width: '100%' }}>
              <Text type="secondary">当前选择: {selectedName || '未选择'}</Text>
              <Tag color="processing">{topic || '未设置订阅过滤器'}</Tag>
            </Space>
          </Card>
        </Col>

        <Col xs={24} lg={15}>
          <Card style={{ marginBottom: 16 }}>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              {/* 状态指示器 */}
              <Space wrap>
                <Badge status={sseConnected ? 'processing' : 'default'} text={sseConnected ? 'SSE 已连接' : 'SSE 未连接'} />
                <Badge status={mqttConnected ? 'success' : 'default'} text={mqttConnected ? 'MQTT 已连接' : 'MQTT 已断开'} />
                <Tag color={mqttProtocol === 'TLS' ? 'green' : mqttProtocol === 'TCP' ? 'orange' : 'default'}>
                  协议: {mqttProtocol || '未连接'}
                </Tag>
                <Tag color="purple">记忆订阅: {subscriptionCount}</Tag>
              </Space>

              {/* 已订阅主题列表 */}
              {subscribedTopics.length > 0 && (
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>已记忆的订阅: </Text>
                  {subscribedTopics.map((t) => (
                    <Tag key={t} color="blue" style={{ marginBottom: 4 }}>{t}</Tag>
                  ))}
                </div>
              )}

              {/* 订阅过滤器输入 */}
              <div>
                <Text strong>订阅过滤器:</Text>
                <Input
                  style={{ marginTop: 8 }}
                  value={topic}
                  onChange={(e) => setTopic(e.target.value)}
                  placeholder="可从左侧域树选择，也可手动输入主题过滤器"
                />
              </div>

              {/* QoS 选择 */}
              <div>
                <Text strong>QoS:</Text>
                <Radio.Group value={qos} onChange={(e) => setQos(e.target.value)} style={{ marginLeft: 8 }}>
                  <Radio.Button value={0}>QoS 0</Radio.Button>
                  <Radio.Button value={1}>QoS 1</Radio.Button>
                  <Radio.Button value={2}>QoS 2</Radio.Button>
                </Radio.Group>
              </div>

              {/* 操作按钮 */}
              <Space wrap>
                {/* 1. 连接 MQTT */}
                <Button
                  type="primary"
                  icon={<LinkOutlined />}
                  onClick={() => void handleConnect()}
                  loading={actionLoading}
                  disabled={mqttConnected}
                >
                  连接 MQTT
                </Button>

                {/* 2. 订阅主题（MQTT 已连接时才可点） */}
                <Button
                  icon={<PlusCircleOutlined />}
                  onClick={() => void handleSubscribe()}
                  loading={actionLoading}
                  disabled={!mqttConnected || !topic.trim()}
                >
                  订阅主题
                </Button>

                {/* 3. 取消订阅 */}
                <Button
                  icon={<MinusCircleOutlined />}
                  onClick={() => void handleCancel()}
                  loading={actionLoading}
                  disabled={!activeTopic}
                >
                  取消订阅
                </Button>

                {/* 4. 断开 MQTT */}
                <Button
                  danger
                  icon={<DisconnectOutlined />}
                  onClick={() => void handleDisconnect()}
                  loading={actionLoading}
                  disabled={!mqttConnected}
                >
                  断开 MQTT
                </Button>

                {/* 活跃订阅状态 */}
                {activeTopic && (
                  <Badge status="processing" text={`活跃订阅: ${activeTopic}`} />
                )}
              </Space>
            </Space>
          </Card>

          <Card
            title={<Space>收到的消息<Tag>{messages.length} 条</Tag></Space>}
            extra={messages.length > 0 && <Button size="small" onClick={clearMessages}>清空</Button>}
          >
            {messages.length === 0 ? (
              <Empty description={mqttConnected ? '等待消息...' : '请先连接 MQTT 并订阅主题'} />
            ) : (
              <List
                dataSource={messages}
                renderItem={(msg) => (
                  <List.Item>
                    <div style={{ width: '100%' }}>
                      <Space style={{ marginBottom: 4 }} wrap>
                        <Tag color="blue">{msg.topic}</Tag>
                        <Text type="secondary">{new Date(msg.timestamp).toLocaleString()}</Text>
                        {msg.meta?.converter && <Tag color="orange">Source: {msg.meta.converter}</Tag>}
                      </Space>
                      <pre
                        style={{
                          background: '#f5f5f5',
                          padding: 8,
                          borderRadius: 4,
                          fontSize: 12,
                          maxHeight: 220,
                          overflow: 'auto',
                          margin: 0,
                        }}
                      >
                        {msg.payload}
                      </pre>
                    </div>
                  </List.Item>
                )}
              />
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Subscribe;
