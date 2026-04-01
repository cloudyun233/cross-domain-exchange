import React, { useEffect, useState, useRef } from 'react';
import { Card, Table, Tag, Typography, Space, Select, Button, Input } from 'antd';
import { ReloadOutlined, WarningOutlined } from '@ant-design/icons';
import { api } from '../services/api';

const { Title, Text } = Typography;

const AuditLog: React.FC = () => {
  const [logs, setLogs] = useState<any[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [filters, setFilters] = useState({ clientId: '', actionType: '' });
  const timerRef = useRef<any>(null);

  const fetchLogs = async (p = page) => {
    setLoading(true);
    try {
      const res = await api.getAuditLogs(p, 20, filters.clientId, filters.actionType);
      if (res.success) {
        setLogs(res.data.records || []);
        setTotal(res.data.total || 0);
      }
    } finally { setLoading(false); }
  };

  useEffect(() => { fetchLogs(); }, [page]);

  // 自动刷新
  useEffect(() => {
    timerRef.current = setInterval(() => fetchLogs(1), 5000);
    return () => clearInterval(timerRef.current);
  }, [filters]);

  const actionTypeColors: Record<string, string> = {
    connect: 'green', disconnect: 'default',
    publish: 'blue', subscribe: 'cyan',
    acl_deny: 'red', acl_allow: 'green',
    auth_success: 'green', auth_fail: 'red',
    deliver: 'purple', publish_fail: 'red',
  };

  const actionTypeLabels: Record<string, string> = {
    connect: '客户端连接', disconnect: '客户端断开',
    publish: '消息发布', subscribe: '主题订阅',
    acl_deny: '权限拒绝', acl_allow: 'ACL通过',
    auth_success: '认证成功', auth_fail: '认证失败',
    deliver: '消息投递', publish_fail: '发布失败',
  };

  const isDanger = (action: string) =>
    ['acl_deny', 'auth_fail', 'publish_fail'].includes(action);

  const columns = [
    { title: '时间', dataIndex: 'createTime', width: 170,
      render: (v: string) => <Text style={{ fontSize: 12 }}>{v}</Text> },
    { title: '客户端', dataIndex: 'clientId', width: 140,
      render: (v: string) => <Tag>{v || '-'}</Tag> },
    { title: '操作类型', dataIndex: 'actionType', width: 120,
      render: (v: string) => (
        <Tag color={actionTypeColors[v] || 'default'} icon={isDanger(v) ? <WarningOutlined /> : undefined}>
          {actionTypeLabels[v] || v}
        </Tag>
      )
    },
    { title: '详情', dataIndex: 'detail', ellipsis: true,
      render: (v: string, record: any) => (
        <Text type={isDanger(record.actionType) ? 'danger' : undefined} style={{ fontSize: 12 }}>
          {v}
        </Text>
      )
    },
    { title: 'IP地址', dataIndex: 'ipAddress', width: 120 },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Title level={4} style={{ margin: 0 }}>审计日志</Title>
        <Space>
          <Input
            placeholder="客户端ID"
            allowClear
            style={{ width: 140 }}
            value={filters.clientId}
            onChange={e => setFilters(prev => ({ ...prev, clientId: e.target.value }))}
            onPressEnter={() => { setPage(1); fetchLogs(1); }}
          />
          <Select
            placeholder="操作类型"
            allowClear
            style={{ width: 140 }}
            value={filters.actionType || undefined}
            onChange={v => setFilters(prev => ({ ...prev, actionType: v || '' }))}
            options={Object.entries(actionTypeLabels).map(([k, v]) => ({ value: k, label: v }))}
          />
          <Button icon={<ReloadOutlined />} onClick={() => { setPage(1); fetchLogs(1); }}>
            刷新
          </Button>
        </Space>
      </Space>

      <Card>
        <Table
          dataSource={logs}
          columns={columns}
          rowKey="id"
          loading={loading}
          rowClassName={(record) => isDanger(record.actionType) ? 'audit-deny' : ''}
          pagination={{
            current: page, pageSize: 20, total,
            onChange: (p) => { setPage(p); fetchLogs(p); },
            showTotal: (t) => `共 ${t} 条记录`,
          }}
          size="small"
        />
      </Card>
    </div>
  );
};

export default AuditLog;
