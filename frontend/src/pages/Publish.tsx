import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Input, Radio, Row, Space, Tag, Tree, Typography, message } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import { api } from '../services/api';

const { TextArea } = Input;
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

const STRUCTURED_SAMPLE = `{
  "patientId": "P20260401001",
  "name": "张三",
  "diagnosis": "常规体检",
  "timestamp": "2026-04-01T08:00:00"
}`;

const PLAIN_TEXT_SAMPLE = '普通文本消息：西南医院已完成病历脱敏';

const formatLabels: Record<string, string> = {
  structured: '结构化',
  text: '文本',
};

const Publish: React.FC = () => {
  const [topicTree, setTopicTree] = useState<DomainTreeNode[]>([]);
  const [selectedNode, setSelectedNode] = useState<DomainTreeNode | null>(null);
  const [selectedTopic, setSelectedTopic] = useState('');
  const [payload, setPayload] = useState(STRUCTURED_SAMPLE);
  const [qos, setQos] = useState(1);
  const [format, setFormat] = useState<'structured' | 'text'>('structured');
  const [publishing, setPublishing] = useState(false);
  const [history, setHistory] = useState<any[]>([]);

  useEffect(() => {
    api.getDomainTree().then((resp) => {
      if (resp.success) {
        setTopicTree(resp.data);
      }
    });
  }, []);

  const handleFormatChange = (nextFormat: 'structured' | 'text') => {
    setFormat(nextFormat);
    setPayload(nextFormat === 'text' ? PLAIN_TEXT_SAMPLE : STRUCTURED_SAMPLE);
  };

  const handlePublish = async () => {
    if (!selectedTopic) {
      message.warning('请先选择目标域');
      return;
    }
    if (!payload.trim()) {
      message.warning('请输入消息内容');
      return;
    }

    setPublishing(true);
    try {
      const res = await api.publish(selectedTopic, payload, qos, format);
      if (res.success) {
        message.success('消息发布成功');
        setHistory((prev: any[]) => [
          { topic: selectedTopic, qos, format, time: new Date().toLocaleString(), success: true },
          ...prev.slice(0, 19),
        ]);
      } else {
        message.error(res.message || '发布失败');
        setHistory((prev: any[]) => [
          { topic: selectedTopic, qos, format, time: new Date().toLocaleString(), success: false, error: res.message },
          ...prev.slice(0, 19),
        ]);
      }
    } catch (e: any) {
      message.error(e.message || '发布失败');
      setHistory((prev: any[]) => [
        { topic: selectedTopic, qos, format, time: new Date().toLocaleString(), success: false, error: e.message },
        ...prev.slice(0, 19),
      ]);
    } finally {
      setPublishing(false);
    }
  };

  return (
    <div>
      <Title level={4}>数据发布</Title>
      <Row gutter={16}>
        <Col xs={24} lg={9}>
          <Card
            title="选择域"
            size="small"
            style={{ marginBottom: 16 }}
            extra={<Tag color="blue">后端域表驱动</Tag>}
          >
            <Tree
              showLine
              blockNode
              defaultExpandAll
              treeData={topicTree as any}
              selectedKeys={selectedTopic ? [selectedTopic] : []}
              onSelect={(_, info) => {
                const node = info.node as any;
                const topicPath = node.topicPath || node.key;
                setSelectedNode(node);
                setSelectedTopic(topicPath);
              }}
              titleRender={(nodeData: any) => (
                <Space size="small">
                  <span>{nodeData.title}</span>
                  {nodeData.domainCode && <Tag color={nodeData.isLeaf ? 'green' : 'cyan'}>{nodeData.domainCode}</Tag>}
                </Space>
              )}
              style={{ maxHeight: 420, overflow: 'auto' }}
            />
            <Alert
              type="info"
              showIcon
              style={{ marginTop: 12 }}
              message={selectedNode?.pathName || '请选择一个域节点'}
              description={selectedTopic || '发布主题会使用所选域对应的 topicPath'}
            />
          </Card>
        </Col>

        <Col xs={24} lg={15}>
          <Card title="消息配置" size="small" style={{ marginBottom: 16 }}>
            <Space direction="vertical" style={{ width: '100%' }} size="middle">
              <div>
                <Text strong>发布主题：</Text>
                <Tag color="processing" style={{ marginLeft: 8 }}>
                  {selectedTopic || '未选择'}
                </Tag>
              </div>

              <div>
                <Text strong>QoS：</Text>
                <Radio.Group value={qos} onChange={(e) => setQos(e.target.value)} style={{ marginLeft: 8 }}>
                  <Radio.Button value={0}>QoS 0</Radio.Button>
                  <Radio.Button value={1}>QoS 1</Radio.Button>
                  <Radio.Button value={2}>QoS 2</Radio.Button>
                </Radio.Group>
              </div>

              <div>
                <Text strong>消息格式：</Text>
                <Radio.Group value={format} onChange={(e) => handleFormatChange(e.target.value)} style={{ marginLeft: 8 }}>
                  <Radio.Button value="structured">结构化</Radio.Button>
                  <Radio.Button value="text">文本</Radio.Button>
                </Radio.Group>
              </div>

              <Text type="secondary">
                结构化模式默认按 JSON 处理；若内容以 {'<'} 开头，则后端按 XML 转 JSON。
              </Text>

              <div>
                <Text strong>消息内容：</Text>
                <TextArea
                  value={payload}
                  onChange={(e) => setPayload(e.target.value)}
                  rows={10}
                  style={{ marginTop: 8, fontFamily: 'monospace', fontSize: 13 }}
                />
              </div>

              <Button
                type="primary"
                icon={<SendOutlined />}
                block
                size="large"
                onClick={handlePublish}
                loading={publishing}
              >
                发布消息
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>

      {history.length > 0 && (
        <Card title="发布历史" size="small">
          {history.map((item: any, index: number) => (
            <div key={index} style={{ padding: '6px 0', borderBottom: '1px solid #f0f0f0' }}>
              <Tag color={item.success ? 'green' : 'red'}>{item.success ? '成功' : '失败'}</Tag>
              <Text code>{item.topic}</Text>
              <Text type="secondary" style={{ marginLeft: 8 }}>
                QoS={item.qos} | {formatLabels[item.format] || item.format} | {item.time}
              </Text>
              {item.error && <Text type="danger" style={{ marginLeft: 8 }}>{item.error}</Text>}
            </div>
          ))}
        </Card>
      )}
    </div>
  );
};

export default Publish;
