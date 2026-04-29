/**
 * 数据发布页 —— 域树选择 + 消息编辑器 + 格式/QoS/Retain 配置 + 发布历史
 *
 * 页面布局：
 * - 左栏：安全域树形选择器，选中叶节点后自动填充发布主题
 * - 右栏：消息内容编辑、QoS/格式/Retain 选项、发布按钮、最近 5 条发布历史
 *
 * 发布流程：
 * 1. 从域树选择目标域 → 自动填充 topicPath
 * 2. 编辑消息内容（结构化 JSON 或纯文本）
 * 3. 选择 QoS 等级和是否 Retain
 * 4. 点击发布 → 调用后端 API → 记录到发布历史
 */
import React, { useEffect, useState } from 'react';
import { Button, Card, Col, Input, Radio, Row, Space, Tag, Tree, Typography, message, Checkbox } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import { api } from '../services/api';
import { usePublish, PublishHistoryItem } from '../contexts/PublishContext';

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

const formatLabels: Record<string, string> = {
  structured: '结构化',
  text: '文本',
};

const Publish: React.FC = () => {
  const [topicTree, setTopicTree] = useState<DomainTreeNode[]>([]);
  const [publishing, setPublishing] = useState(false);
  const {
    selectedTopic,
    selectedNode,
    qos,
    retain,
    format,
    payload,
    history,
    setSelectedTopic,
    setSelectedNode,
    setQos,
    setRetain,
    setFormat,
    setPayload,
    addHistory,
  } = usePublish();

  useEffect(() => {
    api.getDomainTree().then((resp) => {
      if (resp.success) {
        setTopicTree(resp.data);
      }
    });
  }, []);

  /**
   * 发布消息处理：
   * 1. 校验主题和消息内容
   * 2. 调用 api.publish 发送到后端
   * 3. QoS 0 时额外提示"无确认回执"，因为 fire-and-forget 不保证送达
   * 4. 无论成功失败均记录到发布历史
   */
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
      const res = await api.publish(selectedTopic, payload, qos, format, retain);
      if (res.success) {
        if (qos === 0) {
          message.warning('消息已发送，QoS 0 无确认回执，无法确认是否送达');
        } else {
          message.success('消息发布成功');
        }
        addHistory({ topic: selectedTopic, qos, format, time: new Date().toLocaleString(), success: true });
      } else {
        message.error(res.message || '发布失败');
        addHistory({ topic: selectedTopic, qos, format, time: new Date().toLocaleString(), success: false, error: res.message });
      }
    } catch (e: any) {
      message.error(e.message || '发布失败');
      addHistory({ topic: selectedTopic, qos, format, time: new Date().toLocaleString(), success: false, error: e.message });
    } finally {
      setPublishing(false);
    }
  };

  return (
    <div className="page-stack publish-page">
      <div className="page-hero">
        <div>
          <Title level={3} className="page-title">数据发布</Title>
          <Text className="page-subtitle">选择目标安全域，配置 QoS、消息格式与 Retain 后发布跨域数据。</Text>
        </div>
        <Tag color="blue">Publish</Tag>
      </div>
      <Row gutter={[18, 18]} className="publish-layout">
        <Col xs={24} xl={8}>
          <Card
            title="选择域"
            size="small"
            className="publish-domain-card"
            extra={<Tag color="blue">后端域表驱动</Tag>}
          >
            <div className="ios-group publish-tree-box">
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
              />
            </div>
            <Space direction="vertical" size={6} className="publish-selection-summary">
              <Text type="secondary">当前选择: {selectedNode?.pathName || '未选择'}</Text>
              <Tag color="processing">{selectedTopic || '未设置发布主题'}</Tag>
            </Space>
          </Card>
        </Col>

        <Col xs={24} xl={16}>
          <Card title="消息配置" size="small" className="publish-compose-card">
            <div className="publish-topic-row">
              <Text strong>发布主题：</Text>
              <Tag color="processing">{selectedTopic || '未选择'}</Tag>
            </div>

            <div className="publish-composer-grid">
              <div className="publish-message-column">
                <Text strong>消息内容：</Text>
                <TextArea
                  value={payload}
                  onChange={(e) => setPayload(e.target.value)}
                  rows={5}
                  className="code-surface publish-payload-input"
                />
              </div>

              <div className="publish-options-column">
                <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                  <div className="publish-option-item">
                    <Text strong>QoS</Text>
                    <Radio.Group value={qos} onChange={(e) => setQos(e.target.value)}>
                      <Radio.Button value={0}>0</Radio.Button>
                      <Radio.Button value={1}>1</Radio.Button>
                      <Radio.Button value={2}>2</Radio.Button>
                    </Radio.Group>
                  </div>

                  <div className="publish-option-item">
                    <Text strong>消息格式</Text>
                    <Radio.Group value={format} onChange={(e) => setFormat(e.target.value)}>
                      <Radio.Button value="structured">结构化</Radio.Button>
                      <Radio.Button value="text">文本</Radio.Button>
                    </Radio.Group>
                  </div>

                  <div className="publish-option-item publish-retain-row">
                    <Text strong>保留消息</Text>
                    <Checkbox checked={retain} onChange={(e) => setRetain(e.target.checked)}>
                      Retain
                    </Checkbox>
                  </div>

                  <Text type="secondary" className="publish-helper-text">
                    结构化模式默认按 JSON 处理；若内容以 {'<'} 开头，则后端按 XML 转 JSON。
                  </Text>

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
              </div>
            </div>

            <div className="publish-history-panel">
              <div className="publish-history-title">发布历史</div>
              {history.length > 0 ? (
                <div className="publish-history-list">
                  {history.slice(0, 5).map((item: PublishHistoryItem, index: number) => (
                    <div key={index} className="publish-history-item">
                      <Tag color={item.success ? 'green' : 'red'}>{item.success ? '成功' : '失败'}</Tag>
                      <Text code className="publish-history-topic">{item.topic}</Text>
                      <Text type="secondary" className="publish-history-meta">
                        QoS={item.qos} | {formatLabels[item.format] || item.format} | {item.time}
                      </Text>
                      {item.error && <Text type="danger" className="publish-history-error">{item.error}</Text>}
                    </div>
                  ))}
                </div>
              ) : (
                <Text type="secondary">暂无发布记录</Text>
              )}
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Publish;
