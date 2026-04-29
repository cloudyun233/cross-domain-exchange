/**
 * 安全域管理页 —— 域的增删改查 + 父子层级关系维护
 *
 * 功能说明：
 * - 表格展示所有安全域，支持新增、编辑、删除
 * - 父域字段通过向上遍历 parentId 链展示完整层级路径
 * - 弹窗表单中父域选择器排除当前编辑的域（防止自引用）
 */
import React, { useEffect, useState } from 'react';
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { api } from '../services/api';

const { Title, Text } = Typography;

const DomainManage: React.FC = () => {
  const [domains, setDomains] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form] = Form.useForm();

  /**
   * 根据 domainId 向上遍历 parentId 链，拼接完整层级路径
   * 例如：医疗域 / 西南医院
   */
  const getDomainLabel = (domainId?: number | null): string => {
    if (!domainId) return '-';
    const lookup = new Map(domains.map((item) => [item.id, item]));
    const names: string[] = [];
    let current = lookup.get(domainId);

    while (current) {
      names.unshift(current.domainName);
      current = current.parentId ? lookup.get(current.parentId) : undefined;
    }

    return names.join(' / ');
  };

  const fetchDomains = async () => {
    setLoading(true);
    try {
      const res = await api.getDomains();
      if (res.success) setDomains(res.data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchDomains();
  }, []);

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
      void fetchDomains();
    } catch (e: any) {
      message.error(e.message);
    }
  };

  const handleDelete = async (id: number) => {
    await api.deleteDomain(id);
    message.success('安全域删除成功');
    void fetchDomains();
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '域编码', dataIndex: 'domainCode', render: (v: string) => <Tag color="blue">{v}</Tag> },
    { title: '域名称', dataIndex: 'domainName' },
    {
      title: '父域',
      dataIndex: 'parentId',
      render: (value: number | null) => value ? <Tag color="cyan">{getDomainLabel(value)}</Tag> : <Text type="secondary">顶级域</Text>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: (v: number) => v === 1 ? <Tag color="green">启用</Tag> : <Tag color="red">禁用</Tag>,
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
          <Title level={3} className="page-title">安全域管理</Title>
          <Text className="page-subtitle">维护安全域层级、主题前缀与跨域交换边界。</Text>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            setEditingId(null);
            form.resetFields();
            setModalVisible(true);
          }}
        >
          新增域
        </Button>
      </div>

      <Card style={{ marginBottom: 16 }}>
        <Text type="secondary">域编码用于 topic 路径，建议使用英文或拼音；域名称可以使用中文。</Text>
      </Card>

      <Card>
        <Table dataSource={domains} columns={columns} rowKey="id" loading={loading} pagination={false} />
      </Card>

      <Modal
        title={editingId ? '编辑安全域' : '新增安全域'}
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false);
          form.resetFields();
        }}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" onFinish={handleSave} initialValues={{ status: 1 }}>
          <Form.Item name="parentId" label="父域">
            <Select
              allowClear
              placeholder="不选表示顶级域"
              options={domains
                .filter((domain) => domain.id !== editingId)
                .map((domain) => ({ value: domain.id, label: getDomainLabel(domain.id) }))}
            />
          </Form.Item>
          <Form.Item name="domainCode" label="域编码" rules={[{ required: true, message: '请输入域编码' }]}>
            <Input placeholder="如：medical、swh、gov" />
          </Form.Item>
          <Form.Item name="domainName" label="域名称" rules={[{ required: true, message: '请输入域名称' }]}>
            <Input placeholder="如：医疗域、西南医院" />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select options={[{ value: 1, label: '启用' }, { value: 0, label: '禁用' }]} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default DomainManage;
