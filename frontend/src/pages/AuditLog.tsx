import React, { useEffect, useState } from 'react';
import { Card, Table, Tag, Typography, Space, Select, Button, Input, message } from 'antd';
import { DownloadOutlined, ReloadOutlined, WarningOutlined } from '@ant-design/icons';
import { api } from '../services/api';

const { Title, Text } = Typography;

const AuditLog: React.FC = () => {
  const [logs, setLogs] = useState<any[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [page, setPage] = useState(1);
  const [filters, setFilters] = useState({ clientId: '', actionType: '' });

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

  const exportPdf = async () => {
    setExporting(true);
    try {
      const blob = await api.exportAuditLogsPdf(filters.clientId, filters.actionType);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `audit-logs-${new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '')}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      message.success('PDF 导出已开始');
    } catch (error: any) {
      message.error(error?.message || 'PDF 导出失败');
    } finally {
      setExporting(false);
    }
  };

  const actionTypeColors: Record<string, string> = {
    connect: 'green', disconnect: 'default',
    publish: 'blue', publish_fail: 'red', format_convert_fail: 'red',
    subscribe: 'cyan', unsubscribe: 'default',
    acl_deny: 'red', acl_allow: 'green',
    deliver: 'purple',
    mqtt_connect: 'green', mqtt_disconnect: 'default',
    subscribe_close: 'default',
  };

  const actionTypeLabels: Record<string, string> = {
    connect: '客户端连接', disconnect: '客户端断开',
    publish: '消息发布', publish_fail: '发布失败', format_convert_fail: '发布失败',
    subscribe: '主题订阅', unsubscribe: '取消订阅',
    acl_deny: '权限拒绝', acl_allow: 'ACL通过',
    deliver: '消息投递',
    mqtt_connect: '连接MQTT', mqtt_disconnect: '断开MQTT',
    subscribe_close: '关闭会话',
  };

  const isDanger = (action: string) =>
    ['acl_deny', 'publish_fail', 'format_convert_fail'].includes(action);

  const columns = [
    { title: '时间', dataIndex: 'actionTime', width: 170,
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
    <div className="page-stack">
      <div className="page-hero">
        <div>
          <Title level={3} className="page-title">审计日志</Title>
          <Text className="page-subtitle">检索 MQTT 授权事件、拒绝记录与客户端访问轨迹。</Text>
        </div>
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
          <Button type="primary" icon={<DownloadOutlined />} loading={exporting} onClick={exportPdf}>
            导出 PDF
          </Button>
        </Space>
      </div>

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
