import React, { useEffect, useState } from 'react';
import { Card, Table, Button, Modal, Form, Input, Select, Typography, message, Space, Tag, Popconfirm } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { api } from '../services/api';

const { Title } = Typography;

const ClientManage: React.FC = () => {
  const [clients, setClients] = useState<any[]>([]);
  const [domains, setDomains] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form] = Form.useForm();

  const fetchData = async () => {
    setLoading(true);
    try {
      const [c, d] = await Promise.all([api.getClients(), api.getDomains()]);
      if (c.success) setClients(c.data);
      if (d.success) setDomains(d.data);
    } finally { setLoading(false); }
  };

  useEffect(() => { fetchData(); }, []);

  const handleSave = async (values: any) => {
    try {
      if (editingId) {
        await api.updateClient(editingId, values);
        message.success('客户端更新成功');
      } else {
        await api.createClient({ ...values, passwordHash: values.password });
        message.success('客户端创建成功');
      }
      setModalVisible(false);
      form.resetFields();
      setEditingId(null);
      fetchData();
    } catch (e: any) {
      message.error(e.message);
    }
  };

  const handleDelete = async (id: number) => {
    await api.deleteClient(id);
    message.success('客户端删除成功');
    fetchData();
  };

  const roleColors: Record<string, string> = { admin: 'red', producer: 'blue', consumer: 'green' };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: 'ClientID', dataIndex: 'clientId', render: (v: string) => <Tag>{v}</Tag> },
    { title: '所属域', dataIndex: 'domainId', render: (v: number) => {
      const d = domains.find(d => d.id === v);
      return d ? <Tag color="blue">{d.domainName}</Tag> : v;
    }},
    { title: '角色', dataIndex: 'roleType', render: (v: string) => <Tag color={roleColors[v]}>{v}</Tag> },
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
        <Title level={4} style={{ margin: 0 }}>客户端管理</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => {
          setEditingId(null); form.resetFields(); setModalVisible(true);
        }}>新增客户端</Button>
      </Space>

      <Card>
        <Table dataSource={clients} columns={columns} rowKey="id" loading={loading} pagination={false} />
      </Card>

      <Modal title={editingId ? '编辑客户端' : '新增客户端'} open={modalVisible}
        onCancel={() => { setModalVisible(false); form.resetFields(); }}
        onOk={() => form.submit()}>
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Form.Item name="clientId" label="ClientID" rules={[{ required: true }]}>
            <Input placeholder="MQTT接入标识" disabled={!!editingId} />
          </Form.Item>
          {!editingId && (
            <Form.Item name="password" label="密码" rules={[{ required: true }]}>
              <Input.Password placeholder="请输入密码" />
            </Form.Item>
          )}
          <Form.Item name="domainId" label="所属安全域" rules={[{ required: true }]}>
            <Select options={domains.map(d => ({ value: d.id, label: d.domainName }))} placeholder="请选择安全域" />
          </Form.Item>
          <Form.Item name="roleType" label="角色" rules={[{ required: true }]}>
            <Select options={[
              { value: 'admin', label: '管理员 (admin)' },
              { value: 'producer', label: '生产者 (producer)' },
              { value: 'consumer', label: '消费者 (consumer)' },
            ]} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ClientManage;
