/**
 * ACL 规则管理页 —— 访问控制规则的增删改查 + EMQX 实时同步
 *
 * 核心机制：
 * - 每次创建/修改/删除规则后，后端自动推送到 EMQX Broker
 * - "全量同步到 Broker"按钮用于恢复或手动对齐场景，
 *   将数据库中所有规则重新推送到 EMQX，确保两端一致
 * - 支持全局规则（username 为 *）和用户级规则
 */
import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, SafetyOutlined, SyncOutlined } from '@ant-design/icons';
import { api } from '../services/api';

const { Title, Text } = Typography;

const AclManage: React.FC = () => {
  const [rules, setRules] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form] = Form.useForm();

  const fetchRules = async () => {
    setLoading(true);
    try {
      const res = await api.getAclRules();
      if (res.success) setRules(res.data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchRules();
  }, []);

  const handleSave = async (values: any) => {
    try {
      if (editingId) {
        await api.updateAclRule(editingId, values);
        message.success('ACL 规则更新成功，已同步到 Broker');
      } else {
        await api.createAclRule(values);
        message.success('ACL 规则创建成功，已同步到 Broker');
      }
      setModalVisible(false);
      form.resetFields();
      setEditingId(null);
      void fetchRules();
    } catch (e: any) {
      message.error(e.message);
    }
  };

  const handleDelete = async (id: number) => {
    await api.deleteAclRule(id);
    message.success('ACL 规则删除成功，已同步到 Broker');
    void fetchRules();
  };

  /**
   * 全量同步 ACL 规则到 EMQX Broker：
   * 将数据库中所有规则重新推送到 EMQX，用于规则不一致时的手动对齐
   */
  const handleSync = async () => {
    setSyncing(true);
    try {
      const res = await api.syncAcl();
      if (res.success) {
        message.success('ACL 规则已全量同步到 Broker');
      } else {
        message.error(res.message);
      }
    } finally {
      setSyncing(false);
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    {
      title: '用户名',
      dataIndex: 'username',
      render: (v: string) => v === '*' ? <Tag color="volcano">* (全局)</Tag> : <Tag>{v}</Tag>,
    },
    {
      title: '主题过滤器',
      dataIndex: 'topicFilter',
      render: (v: string) => <Tag color="blue" style={{ fontFamily: 'monospace' }}>{v}</Tag>,
    },
    {
      title: '动作',
      dataIndex: 'action',
      render: (v: string) => {
        const colors: Record<string, string> = { publish: 'green', subscribe: 'cyan', all: 'purple' };
        return <Tag color={colors[v] || 'default'}>{v}</Tag>;
      },
    },
    {
      title: '权限',
      dataIndex: 'accessType',
      render: (v: string) => <Tag color={v === 'allow' ? 'green' : 'red'}>{v === 'allow' ? '允许' : '拒绝'}</Tag>,
    },
    {
      title: '操作',
      width: 160,
      render: (_: any, record: any) => (
        <Space>
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() => {
              setEditingId(record.id);
              form.setFieldsValue(record);
              setModalVisible(true);
            }}
          >
            编辑
          </Button>
          <Popconfirm title="确认删除？" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="page-stack">
      <div className="page-hero">
        <div>
          <Title level={3} className="page-title">
            <SafetyOutlined /> ACL 规则管理
          </Title>
          <Text className="page-subtitle">维护发布、订阅与跨域访问控制规则，并同步到 Broker。</Text>
        </div>
        <Space>
          <Button icon={<SyncOutlined spin={syncing} />} onClick={handleSync} loading={syncing}>
            全量同步到 Broker
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditingId(null);
              form.resetFields();
              setModalVisible(true);
            }}
          >
            新增 ACL 规则
          </Button>
        </Space>
      </div>

      <Alert
        message="ACL 说明"
        description="每次创建、修改、删除规则后，系统都会自动推送到 EMQX Broker。全量同步按钮用于恢复或手动对齐场景。"
        type="info"
        showIcon
        closable
        style={{ marginBottom: 16 }}
      />

      <Card>
        <Table dataSource={rules} columns={columns} rowKey="id" loading={loading} pagination={false} />
      </Card>

      <Modal
        title={editingId ? '编辑 ACL 规则' : '新增 ACL 规则'}
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false);
          form.resetFields();
        }}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
            extra="输入 * 表示全局规则"
          >
            <Input placeholder="如: producer_medical_swh 或 *" />
          </Form.Item>
          <Form.Item
            name="topicFilter"
            label="主题过滤器"
            rules={[{ required: true, message: '请输入主题过滤器' }]}
            extra="支持 MQTT 通配符：+（单层）和 #（多层）"
          >
            <Input placeholder="如: cross_domain/medical/#" />
          </Form.Item>
          <Form.Item name="action" label="动作" rules={[{ required: true, message: '请选择动作' }]}>
            <Select
              options={[
                { value: 'publish', label: '发布 (publish)' },
                { value: 'subscribe', label: '订阅 (subscribe)' },
                { value: 'all', label: '全部 (all)' },
              ]}
            />
          </Form.Item>
          <Form.Item name="accessType" label="权限" rules={[{ required: true, message: '请选择权限' }]}>
            <Select
              options={[
                { value: 'allow', label: '允许 (allow)' },
                { value: 'deny', label: '拒绝 (deny)' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AclManage;
