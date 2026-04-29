/**
 * 管理员监控大盘 —— ECharts 实时图表 + 安全域拓扑图
 *
 * 功能模块：
 * - 四项统计指标：Broker 连接数、消息接收/发送总数、JVM 内存
 * - 消息流量折线图：5 秒自动刷新，展示接收/发送两条曲线
 * - 跨域拓扑力导向图：以 EMQX Broker 为中心，展示安全域层级关系
 */
import React, { useEffect, useState } from 'react';
import { Card, Col, Row, Statistic, Tag, Typography } from 'antd';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloudServerOutlined,
  MessageOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import { api } from '../services/api';

const { Title, Text } = Typography;

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

  /** 获取大盘数据：并行请求系统指标和消息统计，5 秒轮询刷新 */
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
    color: ['#007AFF', '#34C759'],
    title: { text: '消息流量（实时）', left: 'center', textStyle: { fontSize: 14, fontWeight: 700, color: '#1D1D1F' } },
    tooltip: { trigger: 'axis', backgroundColor: 'rgba(255,255,255,0.9)', borderColor: 'rgba(0,0,0,0.06)', textStyle: { color: '#1D1D1F' } },
    grid: { left: 42, right: 24, top: 54, bottom: 54 },
    xAxis: {
      type: 'category',
      axisLine: { lineStyle: { color: 'rgba(0,0,0,0.08)' } },
      axisTick: { show: false },
      axisLabel: { color: '#86868B' },
      data: history.map((item: any) => new Date(item.time).toLocaleTimeString('zh-CN', { hour12: false })),
    },
    yAxis: { type: 'value', name: '消息 / 5s', splitLine: { lineStyle: { color: 'rgba(0,0,0,0.05)' } }, axisLabel: { color: '#86868B' } },
    legend: { bottom: 0, textStyle: { color: '#6E6E73' } },
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
    <div className="page-stack">
      <div className="page-hero">
        <div>
          <Title level={3} className="page-title">监控大盘</Title>
          <Text className="page-subtitle">实时观察跨域消息流量、Broker 连接数与安全域拓扑。</Text>
        </div>
        <Tag color="processing">5 秒自动刷新</Tag>
      </div>

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

/**
 * 构建安全域拓扑图 ECharts 配置（力导向布局）
 *
 * 布局策略：
 * - 中心固定节点：EMQX Broker
 * - 递归遍历域树，按深度分配颜色和节点大小
 * - 使用 force 力导向布局，支持拖拽和缩放
 * - 连线样式按深度区分：根域连线较粗，子域连线较细
 */
function buildTopologyOption(domainTree: DomainTreeNode[]) {
  const nodes: any[] = [{
    name: 'EMQX Broker',
    symbolSize: 58,
    fixed: true,
    itemStyle: { color: '#1677ff' },
    x: 180,
    y: 180,
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
      force: { repulsion: 240, edgeLength: 120, gravity: 0.12 },
      label: { show: true },
      edgeLabel: { show: false },
      data: nodes,
      links,
      lineStyle: { curveness: 0.12 },
    }],
  };
}

export default Dashboard;
