import React, { useEffect, useState } from 'react';
import { Card, Table, Button, Modal, Form, Input, Select, Typography, message, Space, Tag, Popconfirm } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { api } from '../services/api';

const { Title } = Typography;

const DomainManage: React.FC = () => {
  const [domains, setDomains] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form] = Form.useForm();

  const fetchDomains = async () => {
    setLoading(true);
    try {
      const res = await api.getDomains();
      if (res.success) setDomains(res.data);
    } finally { setLoading(false); }
  };

  useEffect(() => { fetchDomains(); }, []);

  const handleSave = async (values: any) => {
    try {
      if (editingId) {
        await api.updateDomain(editingId, values);
        message.success('安全域更新成功');
      } else {
        await api.createDomain(values);
        message.success('安全域创建成功');
      }
      setModalVisible(false);
      form.resetFields();
      setEditingId(null);
      fetchDomains();
    } catch (e: any) {
      message.error(e.message);
    }
  };

  const handleDelete = async (id: number) => {
    await api.deleteDomain(id);
    message.success('安全域删除成功');
    fetchDomains();
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '域编码', dataIndex: 'domainCode', render: (v: string) => <Tag color="blue">{v}</Tag> },
    { title: '域名称', dataIndex: 'domainName' },
    { title: '状态', dataIndex: 'status', render: (v: number) => v === 1 ? <Tag color="green">启用</Tag> : <Tag color="red">禁用</Tag> },
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
        <Title level={4} style={{ margin: 0 }}>安全域管理</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => {
          setEditingId(null); form.resetFields(); setModalVisible(true);
        }}>新增安全域</Button>
      </Space>

      <Card>
        <Table dataSource={domains} columns={columns} rowKey="id" loading={loading} pagination={false} />
      </Card>

      <Modal title={editingId ? '编辑安全域' : '新增安全域'} open={modalVisible}
        onCancel={() => { setModalVisible(false); form.resetFields(); }}
        onOk={() => form.submit()}>
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Form.Item name="domainCode" label="域编码" rules={[{ required: true }]}>
            <Input placeholder="如: gov, medical, enterprise" />
          </Form.Item>
          <Form.Item name="domainName" label="域名称" rules={[{ required: true }]}>
            <Input placeholder="如: 政务域" />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue={1}>
            <Select options={[{ value: 1, label: '启用' }, { value: 0, label: '禁用' }]} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default DomainManage;
