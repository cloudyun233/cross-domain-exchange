import React, { useEffect, useState } from 'react';
import { Row, Col, Card, Statistic, Tag, Badge, Space, Typography } from 'antd';
import {
  CloudServerOutlined, MessageOutlined, CheckCircleOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import { api } from '../services/api';

const { Title } = Typography;

const Dashboard: React.FC = () => {
  const [stats, setStats] = useState<any>({});
  const [msgStats, setMsgStats] = useState<any>({});
  const [connStatus, setConnStatus] = useState<any>({});

  const fetchData = async () => {
    try {
      const [m, ms, cs, conn] = await Promise.all([
        api.getMetrics(), api.getMessageStats(), api.getClientStats(), api.getConnectionStatus(),
      ]);
      if (m.success) setStats(m.data);
      if (ms.success) setMsgStats(ms.data);
      if (cs.success) { /* silent, not displaying client stats yet */ }
      if (conn.success) setConnStatus(conn.data);
    } catch (e) { /* silent */ }
  };

  useEffect(() => {
    fetchData();
    const timer = setInterval(fetchData, 5000);
    return () => clearInterval(timer);
  }, []);

  const topologyOption = {
    title: { text: '跨域数据流转拓扑', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'item' },
    series: [{
      type: 'graph', layout: 'force', roam: true, draggable: true,
      force: { repulsion: 200, edgeLength: 150 },
      label: { show: true, fontSize: 11 },
      edgeLabel: { show: true, formatter: '{c}', fontSize: 10 },
      categories: [
        { name: 'Broker', itemStyle: { color: '#1890ff' } },
        { name: '医疗域', itemStyle: { color: '#52c41a' } },
        { name: '政务域', itemStyle: { color: '#faad14' } },
        { name: '企业域', itemStyle: { color: '#722ed1' } },
      ],
      data: [
        { name: 'EMQX Broker', symbolSize: 50, category: 0. },
        { name: '西南医院\n(producer_swu)', symbolSize: 36, category: 1 },
        { name: '社保局\n(consumer_social)', symbolSize: 36, category: 2 },
        { name: '企业C\n(consumer_c)', symbolSize: 36, category: 3 },
        { name: '管理端\n(admin)', symbolSize: 36, category: 0 },
      ],
      links: [
        { source: '西南医院\n(producer_swu)', target: 'EMQX Broker', value: 'PUBLISH', lineStyle: { color: '#52c41a' } },
        { source: 'EMQX Broker', target: '社保局\n(consumer_social)', value: 'SUBSCRIBE', lineStyle: { color: '#faad14' } },
        { source: 'EMQX Broker', target: '企业C\n(consumer_c)', value: 'SUBSCRIBE', lineStyle: { color: '#722ed1', type: 'dashed' } },
        { source: 'EMQX Broker', target: '管理端\n(admin)', value: 'MONITOR', lineStyle: { color: '#1890ff', type: 'dotted' } },
      ],
    }],
  };

  const history: any[] = msgStats.history || [];
  const trafficOption = {
    title: { text: '消息流量 (实时)', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: history.map((_: any, i: number) => `${i * 5}s`),
    },
    yAxis: { type: 'value', name: '消息/s' },
    series: [
      { name: '接收', data: history.map((h: any) => h.messagesIn || 0), type: 'line', smooth: true, areaStyle: { opacity: 0.2 }, itemStyle: { color: '#1890ff' } },
      { name: '发送', data: history.map((h: any) => h.messagesOut || 0), type: 'line', smooth: true, areaStyle: { opacity: 0.2 }, itemStyle: { color: '#52c41a' } },
    ],
    legend: { bottom: 0 },
  };

  const protocolColor = connStatus.protocol === 'TLS' ? 'green' : connStatus.protocol === 'TCP' ? 'orange' : 'red';

  return (
    <div>
      <Space style={{ marginBottom: 16 }} align="center">
        <Title level={4} style={{ margin: 0 }}>监控大盘</Title>
        <Badge
          status={connStatus.connected ? 'success' : 'error'}
          text={connStatus.connected ? '已连接' : '未连接'}
        />
        <Tag color={protocolColor}>协议: {connStatus.protocol || '未知'}</Tag>
      </Space>

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card className="stat-card" hoverable>
            <Statistic title="Broker连接数" value={stats.jvmUsedMemory || 0}
              prefix={<CloudServerOutlined style={{ color: '#1890ff' }} />}
              suffix="个" />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="stat-card" hoverable>
            <Statistic title="消息接收" value={msgStats['messages.received'] || 0}
              prefix={<MessageOutlined style={{ color: '#52c41a' }} />} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="stat-card" hoverable>
            <Statistic title="消息发送" value={msgStats['messages.sent'] || 0}
              prefix={<CheckCircleOutlined style={{ color: '#faad14' }} />} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="stat-card" hoverable>
            <Statistic title="JVM内存" value={stats.jvmUsedMemory || 0}
              prefix={<ClockCircleOutlined style={{ color: '#722ed1' }} />}
              suffix="MB" />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <Card><ReactECharts option={topologyOption} style={{ height: 360 }} /></Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card><ReactECharts option={trafficOption} style={{ height: 360 }} /></Card>
        </Col>
      </Row>
    </div>
  );
};

export default Dashboard;
