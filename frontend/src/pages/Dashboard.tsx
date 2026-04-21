import React, { useEffect, useState } from 'react';
import { Card, Col, Row, Statistic, Typography } from 'antd';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloudServerOutlined,
  MessageOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import { api } from '../services/api';

const { Title } = Typography;

interface DomainTreeNode {
  key: string;
  title: string;
  domainCode?: string;
  children?: DomainTreeNode[];
}

const DOMAIN_COLORS = ['#52c41a', '#faad14', '#13c2c2', '#722ed1', '#1677ff'];

const Dashboard: React.FC = () => {
  const [stats, setStats] = useState<any>({});
  const [msgStats, setMsgStats] = useState<any>({});
  const [domainTree, setDomainTree] = useState<DomainTreeNode[]>([]);

  const fetchDashboardData = async () => {
    try {
      const [metricsResp, messageResp] = await Promise.all([
        api.getMetrics(),
        api.getMessageStats(),
      ]);

      if (metricsResp.success) {
        setStats(metricsResp.data);
      }
      if (messageResp.success) {
        setMsgStats(messageResp.data);
      }
    } catch {
      // ignore dashboard polling errors
    }
  };

  const fetchDomainTree = async () => {
    try {
      const resp = await api.getDomainTree();
      if (resp.success) {
        setDomainTree(resp.data);
      }
    } catch {
      // ignore topology errors
    }
  };

  useEffect(() => {
    void fetchDashboardData();
    void fetchDomainTree();
    const timer = setInterval(() => {
      void fetchDashboardData();
    }, 5000);
    return () => clearInterval(timer);
  }, []);

  const history: any[] = msgStats.history || [];
  const trafficOption = {
    title: { text: '消息流量（实时）', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: history.map((item: any) => new Date(item.time).toLocaleTimeString('zh-CN', { hour12: false })),
    },
    yAxis: { type: 'value', name: '消息 / 5s' },
    legend: { bottom: 0 },
    series: [
      {
        name: '接收',
        data: history.map((item: any) => item.messagesIn || 0),
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.18 },
        itemStyle: { color: '#1677ff' },
      },
      {
        name: '发送',
        data: history.map((item: any) => item.messagesOut || 0),
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.18 },
        itemStyle: { color: '#52c41a' },
      },
    ],
  };

  const topologyOption = buildTopologyOption(domainTree);

  return (
    <div>
      <Title level={4} style={{ marginTop: 0 }}>监控大盘</Title>

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable className="stat-card">
            <Statistic
              title="Broker 连接数"
              value={msgStats.onlineConnections || 0}
              prefix={<CloudServerOutlined style={{ color: '#1677ff' }} />}
              suffix="个"
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable className="stat-card">
            <Statistic
              title="消息接收总数"
              value={msgStats.totalMessagesReceived || 0}
              prefix={<MessageOutlined style={{ color: '#52c41a' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable className="stat-card">
            <Statistic
              title="消息发送总数"
              value={msgStats.totalMessagesSent || 0}
              prefix={<CheckCircleOutlined style={{ color: '#faad14' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable className="stat-card">
            <Statistic
              title="JVM 内存"
              value={stats.jvmUsedMemory || 0}
              prefix={<ClockCircleOutlined style={{ color: '#722ed1' }} />}
              suffix="MB"
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <Card>
            <ReactECharts option={topologyOption} style={{ height: 360 }} />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card>
            <ReactECharts option={trafficOption} style={{ height: 360 }} />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

function buildTopologyOption(domainTree: DomainTreeNode[]) {
  const nodes: any[] = [{
    name: 'EMQX Broker',
    symbolSize: 52,
    fixed: true,
    itemStyle: { color: '#1677ff' },
    x: 0,
    y: 0,
  }];
  const links: any[] = [];

  const roots = domainTree.length === 1 && domainTree[0]?.children?.length ? domainTree[0].children! : domainTree;

  const walk = (items: DomainTreeNode[], parentName: string, depth: number) => {
    items.forEach((item) => {
      const nodeName = item.domainCode ? `${item.title}\n(${item.domainCode})` : item.title;
      const color = DOMAIN_COLORS[Math.min(depth, DOMAIN_COLORS.length - 1)];

      nodes.push({
        name: nodeName,
        symbolSize: Math.max(28, 42 - depth * 2),
        itemStyle: { color },
        label: { fontSize: depth > 1 ? 11 : 12 },
      });
      links.push({
        source: parentName,
        target: nodeName,
        value: depth === 0 ? 'DOMAIN' : 'CHILD',
        lineStyle: { color, width: depth === 0 ? 2.4 : 1.6, opacity: 0.82 },
      });

      if (item.children?.length) {
        walk(item.children, nodeName, depth + 1);
      }
    });
  };

  walk(roots, 'EMQX Broker', 0);

  return {
    title: { text: '跨域数据流转拓扑', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'item' },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      force: { repulsion: 240, edgeLength: 120, gravity: 0.08 },
      label: { show: true },
      edgeLabel: { show: false },
      data: nodes,
      links,
      lineStyle: { curveness: 0.12 },
    }],
  };
}

export default Dashboard;
