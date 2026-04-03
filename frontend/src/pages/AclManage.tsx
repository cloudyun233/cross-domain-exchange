import React, { useEffect, useState } from 'react';
import { Card, Table, Button, Modal, Form, Input, Select, Typography, message, Space, Tag, Popconfirm, Alert } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SyncOutlined, SafetyOutlined } from '@ant-design/icons';
import { api } from '../services/api';

const { Title } = Typography;

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
    } finally { setLoading(false); }
  };

  useEffect(() => { fetchRules(); }, []);

  const handleSave = async (values: any) => {
    try {
      if (editingId) {
        await api.updateAclRule(editingId, values);
        message.success('ACL规则更新成功（已同步到Broker）');
      } else {
        await api.createAclRule(values);
        message.success('ACL规则创建成功（已同步到Broker）');
      }
      setModalVisible(false);
      form.resetFields();
      setEditingId(null);
      fetchRules();
    } catch (e: any) {
      message.error(e.message);
    }
  };

  const handleDelete = async (id: number) => {
    await api.deleteAclRule(id);
    message.success('ACL规则删除成功（已同步到Broker）');
    fetchRules();
  };

  const handleSync = async () => {
    setSyncing(true);
    try {
      const res = await api.syncAcl();
      if (res.success) message.success('ACL规则全量同步到Broker成功');
      else message.error(res.message);
    } finally { setSyncing(false); }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '客户端', dataIndex: 'clientId', render: (v: string) =>
      v === '*' ? <Tag color="volcano">* (全局)</Tag> : <Tag>{v}</Tag>
    },
    { title: '主题过滤器', dataIndex: 'topicFilter', render: (v: string) =>
      <Tag color="blue" style={{ fontFamily: 'monospace' }}>{v}</Tag>
    },
    { title: '动作', dataIndex: 'action', render: (v: string) => {
      const colors: Record<string, string> = { publish: 'green', subscribe: 'cyan', all: 'purple' };
      return <Tag color={colors[v] || 'default'}>{v}</Tag>;
    }},
    { title: '权限', dataIndex: 'accessType', render: (v: string) =>
      <Tag color={v === 'allow' ? 'green' : 'red'}>{v === 'allow' ? '允许' : '拒绝'}</Tag>
    },
    {
      title: '操作', width: 160,
      render: (_: any, record: any) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => {
            setEditingId(record.id);
            form.setFieldsValue(record);
            setModalVisible(true);
          }}>编辑</Button>
          <Popconfirm title="确认删除?" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Title level={4} style={{ margin: 0 }}><SafetyOutlined /> ACL规则管理</Title>
        <Space>
          <Button icon={<SyncOutlined spin={syncing} />} onClick={handleSync} loading={syncing}>
            全量同步到Broker
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => {
            setEditingId(null); form.resetFields(); setModalVisible(true);
          }}>新增ACL规则</Button>
        </Space>
      </Space>

      <Alert
        message="ACL规则说明"
        description="每次创建/修改/删除规则后，系统会自动实时推送到EMQX Broker。全量同步 按钮用于容灾恢复场景。"
        type="info" showIcon closable style={{ marginBottom: 16 }}
      />

      <Card>
        <Table dataSource={rules} columns={columns} rowKey="id" loading={loading} pagination={false} />
      </Card>

      <Modal title={editingId ? '编辑ACL规则' : '新增ACL规则'} open={modalVisible}
        onCancel={() => { setModalVisible(false); form.resetFields(); }}
        onOk={() => form.submit()}>
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Form.Item name="clientId" label="客户端ID" rules={[{ required: true }]}
            extra="输入*表示全局规则">
            <Input placeholder="如: producer_swu 或 *" />
          </Form.Item>
          <Form.Item name="topicFilter" label="主题过滤器" rules={[{ required: true }]}
            extra="支持MQTT通配符: + (单层) # (多层)">
            <Input placeholder="如: /cross_domain/medical/#" />
          </Form.Item>
          <Form.Item name="action" label="动作" rules={[{ required: true }]}>
            <Select options={[
              { value: 'publish', label: '发布 (publish)' },
              { value: 'subscribe', label: '订阅 (subscribe)' },
              { value: 'all', label: '全部 (all)' },
            ]} />
          </Form.Item>
          <Form.Item name="accessType" label="权限" rules={[{ required: true }]}>
            <Select options={[
              { value: 'allow', label: '允许 (allow)' },
              { value: 'deny', label: '拒绝 (deny)' },
            ]} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AclManage;
