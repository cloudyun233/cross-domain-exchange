import React, { useEffect, useState } from 'react';
import { Button, Card, Col, Radio, Row, Space, Tag, Tree, Typography, message, Input } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import { api } from '../services/api';

const { TextArea } = Input;
const { Title, Text } = Typography;

const STRUCTURED_SAMPLE = `{
  "patientId": "P20260401001",
  "name": "张三",
  "diagnosis": "常规体检",
  "timestamp": "2026-04-01T08:00:00"
}`;

const PLAIN_TEXT_SAMPLE = '普通文本消息：供应链订单已完成签收';

const formatLabels: Record<string, string> = {
  structured: 'JSON（XML兼容）',
  text: '普通消息',
};

const Publish: React.FC = () => {
  const [topicTree, setTopicTree] = useState<any[]>([]);
  const [selectedTopic, setSelectedTopic] = useState('');
  const [payload, setPayload] = useState(STRUCTURED_SAMPLE);
  const [qos, setQos] = useState(1);
  const [format, setFormat] = useState<'structured' | 'text'>('structured');
  const [publishing, setPublishing] = useState(false);
  const [history, setHistory] = useState<any[]>([]);

  useEffect(() => {
    api.getTopicTree().then((resp) => {
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
      message.warning('请选择目标主题');
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
      <Title level={4}>数据发布（生产者视角）</Title>
      <Row gutter={16}>
        <Col xs={24} lg={10}>
          <Card title="选择目标主题" size="small" style={{ marginBottom: 16 }}>
            <Tree
              treeData={topicTree}
              onSelect={(keys) => {
                if (keys.length > 0) {
                  setSelectedTopic(keys[0] as string);
                }
              }}
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
                <Radio.Group value={qos} onChange={(e) => setQos(e.target.value)} style={{ marginLeft: 8 }}>
                  <Radio.Button value={0}>QoS 0（最多一次）</Radio.Button>
                  <Radio.Button value={1}>QoS 1（至少一次）</Radio.Button>
                  <Radio.Button value={2}>QoS 2（精确一次）</Radio.Button>
                </Radio.Group>
              </div>
              <div>
                <Text strong>数据格式：</Text>
                <Radio.Group
                  value={format}
                  onChange={(e) => handleFormatChange(e.target.value)}
                  style={{ marginLeft: 8 }}
                >
                  <Radio.Button value="structured">JSON（XML兼容）</Radio.Button>
                  <Radio.Button value="text">普通消息</Radio.Button>
                </Radio.Group>
              </div>
              <div>
                <Text type="secondary">
                  {format === 'structured'
                    ? '可直接输入 JSON；若内容以 < 开头则按 XML 解析并自动转为 JSON。'
                    : '普通消息将按原始文本直接发送。'}
                </Text>
              </div>
              <div>
                <Text strong>消息体：</Text>
                <TextArea
                  value={payload}
                  onChange={(e) => setPayload(e.target.value)}
                  rows={8}
                  style={{ marginTop: 4, fontFamily: 'monospace', fontSize: 13 }}
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
