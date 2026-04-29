/**
 * 用户/客户端管理页 —— 账号增删改查 + 角色-域校验
 *
 * 业务规则：
 * - 管理员（admin）无需绑定安全域，domainId 置为 null
 * - 生产者（producer）和消费者（consumer）必须绑定安全域
 * - 新建用户时系统自动生成 clientId（格式：用户名_001）
 * - 编辑模式下用户名不可修改，密码字段不显示
 */
import React, { useEffect, useState } from 'react';
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { api } from '../services/api';

const { Title, Text } = Typography;

const ClientManage: React.FC = () => {
  const [users, setUsers] = useState<any[]>([]);
  const [domains, setDomains] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form] = Form.useForm();
  const selectedRole = Form.useWatch('roleType', form);

  /**
   * 根据 domainId 向上遍历 parentId 链，拼接完整层级路径
   * 无 domainId 时返回"全域"（管理员场景）
   */
  const getDomainLabel = (domainId?: number | null): string => {
    if (!domainId) return '全域';
    const lookup = new Map(domains.map((item) => [item.id, item]));
    const names: string[] = [];
    let current = lookup.get(domainId);

    while (current) {
      names.unshift(current.domainName);
      current = current.parentId ? lookup.get(current.parentId) : undefined;
    }

    return names.join(' / ');
  };

  const fetchData = async () => {
    setLoading(true);
    try {
      const [usersResp, domainsResp] = await Promise.all([api.getClients(), api.getDomains()]);
      if (usersResp.success) setUsers(usersResp.data);
      if (domainsResp.success) setDomains(domainsResp.data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchData();
  }, []);

  /**
   * 保存用户：
   * - 管理员角色强制将 domainId 置为 null（管理员无域绑定）
   * - 新建时传入 passwordHash 字段，编辑时不传
   */
  const handleSave = async (values: any) => {
    const payload = {
      ...values,
      domainId: values.roleType === 'admin' ? null : values.domainId,
    };

    try {
      if (editingId) {
        await api.updateClient(editingId, payload);
        message.success('用户更新成功');
      } else {
        await api.createClient({ ...payload, passwordHash: values.password });
        message.success('用户创建成功');
      }
      setModalVisible(false);
      form.resetFields();
      setEditingId(null);
      void fetchData();
    } catch (e: any) {
      message.error(e.message);
    }
  };

  const handleDelete = async (id: number) => {
    await api.deleteClient(id);
    message.success('用户删除成功');
    void fetchData();
  };

  const openCreateModal = () => {
    setEditingId(null);
    form.resetFields();
    form.setFieldsValue({ roleType: 'consumer' });
    setModalVisible(true);
  };

  const openEditModal = (record: any) => {
    setEditingId(record.id);
    form.setFieldsValue({
      username: record.username,
      domainId: record.domainId,
      roleType: record.roleType,
    });
    setModalVisible(true);
  };

  const roleColors: Record<string, string> = {
    admin: 'red',
    producer: 'blue',
    consumer: 'green',
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '用户名', dataIndex: 'username', render: (value: string) => <Tag>{value}</Tag> },
    {
      title: '所属域',
      dataIndex: 'domainId',
      render: (value: number | null) => value ? <Tag color="blue">{getDomainLabel(value)}</Tag> : <Tag>全域</Tag>,
    },
    {
      title: '角色',
      dataIndex: 'roleType',
      render: (value: string) => <Tag color={roleColors[value] || 'default'}>{value}</Tag>,
    },
    {
      title: '操作',
      width: 160,
      render: (_: any, record: any) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEditModal(record)}>
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
          <Title level={3} className="page-title">用户管理</Title>
          <Text className="page-subtitle">管理系统账号、角色与所属安全域，保持权限边界清晰。</Text>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
          新增用户
        </Button>
      </div>

      <Card>
        <Table dataSource={users} columns={columns} rowKey="id" loading={loading} pagination={false} />
      </Card>

      <Modal
        title={editingId ? '编辑用户' : '新增用户'}
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false);
          form.resetFields();
        }}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input placeholder="请输入唯一用户名" disabled={!!editingId} />
          </Form.Item>
          {!editingId && (
            <>
              <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
                <Input.Password placeholder="请输入密码" />
              </Form.Item>
              <Text type="secondary">系统会自动生成 clientId，格式为 用户名_001。</Text>
            </>
          )}
          <Form.Item name="roleType" label="角色" rules={[{ required: true, message: '请选择角色' }]}>
            <Select
              options={[
                { value: 'admin', label: '管理员 (admin)' },
                { value: 'producer', label: '生产者 (producer)' },
                { value: 'consumer', label: '消费者 (consumer)' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="domainId"
            label="所属安全域"
            extra={selectedRole === 'admin' ? '管理员可留空，表示全域。' : undefined}
            rules={[({ getFieldValue }) => ({
              validator(_, value) {
                if (getFieldValue('roleType') === 'admin' || value) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error('请选择所属安全域'));
              },
            })]}
          >
            <Select
              allowClear
              options={domains.map((domain) => ({ value: domain.id, label: getDomainLabel(domain.id) }))}
              placeholder="请选择安全域"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ClientManage;
