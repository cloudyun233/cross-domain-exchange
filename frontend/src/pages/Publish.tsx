import React, { useEffect, useState } from 'react';
import { Card, Select, Input, Button, Radio, message, Tree, Typography, Row, Col, Tag, Space } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import { api } from '../services/api';

const { TextArea } = Input;
const { Title, Text } = Typography;

const Publish: React.FC = () => {
  const [topicTree, setTopicTree] = useState<any[]>([]);
  const [selectedTopic, setSelectedTopic] = useState('');
  const [payload, setPayload] = useState('{\n  "patientId": "P20260401001",\n  "name": "张三",\n  "diagnosis": "常规体检",\n  "timestamp": "2026-04-01T08:00:00"\n}');
  const [qos, setQos] = useState(1);
  const [format, setFormat] = useState('json');
  const [publishing, setPublishing] = useState(false);
  const [history, setHistory] = useState<any[]>([]);

  useEffect(() => {
    api.getTopicTree().then(r => { if (r.success) setTopicTree(r.data); });
  }, []);

  const handlePublish = async () => {
    if (!selectedTopic) { message.warning('请选择目标主题'); return; }
    if (!payload.trim()) { message.warning('请输入消息内容'); return; }
    setPublishing(true);
    try {
      const res = await api.publish(selectedTopic, payload, qos, format);
      if (res.success) {
        message.success('消息发布成功！');
        setHistory(prev => [
          { topic: selectedTopic, qos, format, time: new Date().toLocaleString(), success: true },
          ...prev.slice(0, 19),
        ]);
      } else {
        message.error(res.message || '发布失败');
        setHistory(prev => [
          { topic: selectedTopic, qos, format, time: new Date().toLocaleString(), success: false, error: res.message },
          ...prev.slice(0, 19),
        ]);
      }
    } catch (e: any) {
      message.error('发布失败: ' + e.message);
    } finally {
      setPublishing(false);
    }
  };

  const xmlSample = `<patient>
  <id>P20260401001</id>
  <name>张三</name>
  <diagnosis>常规体检</diagnosis>
</patient>`;

  return (
    <div>
      <Title level={4}>数据发布（生产者视角）</Title>
      <Row gutter={16}>
        <Col xs={24} lg={10}>
          <Card title="选择目标主题" size="small" style={{ marginBottom: 16 }}>
            <Tree
              treeData={topicTree}
              onSelect={(keys) => { if (keys.length > 0) setSelectedTopic(keys[0] as string); }}
              selectedKeys={selectedTopic ? [selectedTopic] : []}
              defaultExpandAll
              style={{ maxHeight: 300, overflow: 'auto' }}
            />
            {selectedTopic && (
              <Tag color="blue" style={{ marginTop: 8 }}>已选: {selectedTopic}</Tag>
            )}
          </Card>
        </Col>

        <Col xs={24} lg={14}>
          <Card title="消息配置" size="small" style={{ marginBottom: 16 }}>
            <Space direction="vertical" style={{ width: '100%' }} size="middle">
              <div>
                <Text strong>QoS等级：</Text>
                <Radio.Group value={qos} onChange={e => setQos(e.target.value)} style={{ marginLeft: 8 }}>
                  <Radio.Button value={0}>QoS 0 (最多一次)</Radio.Button>
                  <Radio.Button value={1}>QoS 1 (至少一次)</Radio.Button>
                  <Radio.Button value={2}>QoS 2 (精确一次)</Radio.Button>
                </Radio.Group>
              </div>
              <div>
                <Text strong>数据格式：</Text>
                <Radio.Group value={format} onChange={e => {
                  setFormat(e.target.value);
                  if (e.target.value === 'xml') setPayload(xmlSample);
                  else setPayload('{\n  "patientId": "P20260401001",\n  "name": "张三"\n}');
                }} style={{ marginLeft: 8 }}>
                  <Radio.Button value="json">JSON</Radio.Button>
                  <Radio.Button value="xml">XML (将自动转换为JSON)</Radio.Button>
                </Radio.Group>
              </div>
              <div>
                <Text strong>消息体：</Text>
                <TextArea
                  value={payload}
                  onChange={e => setPayload(e.target.value)}
                  rows={8}
                  style={{ marginTop: 4, fontFamily: 'monospace', fontSize: 13 }}
                />
              </div>
              <Button
                type="primary" icon={<SendOutlined />} block size="large"
                onClick={handlePublish} loading={publishing}
              >
                发布消息
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>

      {history.length > 0 && (
        <Card title="发布历史" size="small">
          {history.map((h, i) => (
            <div key={i} style={{ padding: '6px 0', borderBottom: '1px solid #f0f0f0' }}>
              <Tag color={h.success ? 'green' : 'red'}>{h.success ? '成功' : '失败'}</Tag>
              <Text code>{h.topic}</Text>
              <Text type="secondary" style={{ marginLeft: 8 }}>QoS={h.qos} | {h.format} | {h.time}</Text>
              {h.error && <Text type="danger" style={{ marginLeft: 8 }}>{h.error}</Text>}
            </div>
          ))}
        </Card>
      )}
    </div>
  );
};

export default Publish;
