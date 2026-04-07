import React, { useEffect, useState } from 'react';
import { Badge, Button, Card, Col, Empty, Input, List, Radio, Row, Space, Tag, Tree, Typography } from 'antd';
import { PauseCircleOutlined, PlayCircleOutlined } from '@ant-design/icons';
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
  const {
    topic,
    qos,
    listening,
    activeTopic,
    messages,
    selectedKey,
    selectedName,
    setTopic,
    setQos,
    setSelectedKey,
    setSelectedName,
    startListening,
    stopListening,
    clearMessages,
  } = useSubscribe();

  useEffect(() => {
    api.getDomainTree().then((resp) => {
      if (resp.success) {
        setDomainTree(resp.data);
      }
    });
  }, []);

  return (
    <div>
      <Title level={4}>数据订阅</Title>

      <Row gutter={16}>
        <Col xs={24} lg={9}>
          <Card title="选择订阅域" extra={<Tag color="blue">后端域表驱动</Tag>} style={{ marginBottom: 16 }}>
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
            <Space direction="vertical" size={6} style={{ marginTop: 12, width: '100%' }}>
              <Text type="secondary">当前选择：{selectedName || '未选择'}</Text>
              <Tag color="processing">{topic || '未设置订阅过滤器'}</Tag>
            </Space>
          </Card>
        </Col>

        <Col xs={24} lg={15}>
          <Card style={{ marginBottom: 16 }}>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <div>
                <Text strong>订阅过滤器：</Text>
                <Input
                  style={{ marginTop: 8 }}
                  value={topic}
                  onChange={(e) => setTopic(e.target.value)}
                  placeholder="可从左侧树选择，也可手动输入主题过滤器"
                />
              </div>

              <div>
                <Text strong>QoS：</Text>
                <Radio.Group value={qos} onChange={(e) => setQos(e.target.value)} style={{ marginLeft: 8 }}>
                  <Radio.Button value={0}>QoS 0</Radio.Button>
                  <Radio.Button value={1}>QoS 1</Radio.Button>
                  <Radio.Button value={2}>QoS 2</Radio.Button>
                </Radio.Group>
              </div>

              <Space wrap>
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
                  text={listening ? `监听中：${activeTopic || topic}` : '未监听'}
                />
              </Space>
            </Space>
          </Card>

          <Card
            title={<Space>收到的消息 <Tag>{messages.length} 条</Tag></Space>}
            extra={messages.length > 0 && <Button size="small" onClick={clearMessages}>清空</Button>}
          >
            {messages.length === 0 ? (
              <Empty description={listening ? '连接已建立，等待消息...' : '选择域节点后开始监听'} />
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
