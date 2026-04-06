import React, { useEffect, useState } from 'react';
import { Card, Typography, Button, Tag, Row, Col, Alert, message, Spin, Result } from 'antd';
import {
  ThunderboltOutlined,
  WifiOutlined,
  CloudOutlined,
  AlertOutlined,
  CloseCircleOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import { api } from '../services/api';
import { useAuth } from '../contexts/AuthContext';

const { Title, Text } = Typography;

interface Preset {
  name: string;
  delay: number;
  loss: number;
  bandwidth: number;
  description: string;
}

const presetIcons: Record<string, React.ReactNode> = {
  '无限制': <CheckCircleOutlined style={{ fontSize: 32, color: '#52c41a' }} />,
  '标准网络': <WifiOutlined style={{ fontSize: 32, color: '#1890ff' }} />,
  '政务跨域波动': <CloudOutlined style={{ fontSize: 32, color: '#722ed1' }} />,
  '普通弱网': <AlertOutlined style={{ fontSize: 32, color: '#fa8c16' }} />,
  '极端弱网': <CloseCircleOutlined style={{ fontSize: 32, color: '#f5222d' }} />,
};

const presetColors: Record<string, string> = {
  '无限制': '#52c41a',
  '标准网络': '#1890ff',
  '政务跨域波动': '#722ed1',
  '普通弱网': '#fa8c16',
  '极端弱网': '#f5222d',
};

const NetworkSimulate: React.FC = () => {
  const { user } = useAuth();
  const [presets, setPresets] = useState<Preset[]>([]);
  const [selectedPreset, setSelectedPreset] = useState<string>('无限制');
  const [currentPreset, setCurrentPreset] = useState<string>('无限制');
  const [applying, setApplying] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.getNetworkPresets().then(res => {
      if (res.success) {
        setPresets(res.data);
        setSelectedPreset('无限制');
        setCurrentPreset('无限制');
      }
    }).finally(() => setLoading(false));
  }, []);

  const handleApply = async () => {
    const preset = presets.find(p => p.name === selectedPreset);
    if (!preset) return;

    setApplying(true);
    try {
      const res = await api.simulateNetwork(preset.delay, preset.loss, preset.bandwidth);
      if (res.success) {
        message.success(res.message || '弱网模拟已设置');
        setCurrentPreset(selectedPreset);
      } else {
        message.error(res.message);
      }
    } catch (e: any) {
      message.error('设置失败: ' + e.message);
    } finally {
      setApplying(false);
    }
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: 400 }}>
        <Spin size="large" tip="加载中..." />
      </div>
    );
  }

  if (user?.roleType !== 'ADMIN') {
    return (
      <Result
        status="403"
        title="无访问权限"
        subTitle="弱网模拟功能仅限管理员使用"
      />
    );
  }

  return (
    <div>
      <Title level={4}><ThunderboltOutlined /> 弱网模拟（Linux TC）</Title>

      <Alert
        message="说明"
        description="弱网模拟需要在Linux Docker环境中运行，通过Linux TC (traffic control) 模拟跨域网络延迟、丢包和带宽限制。选择预设场景后点击应用生效。"
        type="info"
        showIcon
        style={{ marginBottom: 24 }}
      />

      <Row gutter={[16, 16]}>
        {presets.map(preset => {
          const isSelected = selectedPreset === preset.name;
          const isCurrent = currentPreset === preset.name;
          const color = presetColors[preset.name] || '#1890ff';

          return (
            <Col xs={24} sm={12} lg={8} xl={8} xxl={4} key={preset.name}>
              <Card
                hoverable
                onClick={() => setSelectedPreset(preset.name)}
                style={{
                  borderRadius: 12,
                  border: isSelected ? `2px solid ${color}` : '2px solid transparent',
                  boxShadow: isSelected ? `0 4px 12px ${color}33` : '0 2px 8px rgba(0,0,0,0.08)',
                  transition: 'all 0.3s ease',
                  background: isSelected ? `${color}08` : '#fff',
                  height: '100%',
                }}
                styles={{ body: { padding: 20 } }}
              >
                <div style={{ textAlign: 'center', marginBottom: 16 }}>
                  {presetIcons[preset.name]}
                </div>

                <Title level={5} style={{ textAlign: 'center', marginBottom: 8 }}>
                  {preset.name}
                  {isCurrent && (
                    <Tag color="green" style={{ marginLeft: 8, fontSize: 10 }}>当前</Tag>
                  )}
                </Title>

                <Text type="secondary" style={{ display: 'block', textAlign: 'center', marginBottom: 16, minHeight: 40 }}>
                  {preset.description}
                </Text>

                <div style={{ display: 'flex', justifyContent: 'center', gap: 8, flexWrap: 'wrap' }}>
                  <Tag color={preset.delay === 0 ? 'green' : 'blue'}>
                    延迟: {preset.delay}ms
                  </Tag>
                  <Tag color={preset.loss === 0 ? 'green' : preset.loss > 15 ? 'red' : 'orange'}>
                    丢包: {preset.loss}%
                  </Tag>
                  <Tag color={preset.bandwidth === 0 ? 'green' : 'purple'}>
                    带宽: {preset.bandwidth === 0 ? '无限制' : `${preset.bandwidth}Mbps`}
                  </Tag>
                </div>
              </Card>
            </Col>
          );
        })}
      </Row>

      <Card style={{ marginTop: 24, textAlign: 'center' }}>
        <div style={{ marginBottom: 16 }}>
          <Text type="secondary">
            当前选中: <Text strong>{selectedPreset}</Text>
            {selectedPreset !== currentPreset && (
              <Text type="warning" style={{ marginLeft: 8 }}>
                (未应用)
              </Text>
            )}
          </Text>
        </div>
        <Button
          type="primary"
          size="large"
          onClick={handleApply}
          loading={applying}
          disabled={selectedPreset === currentPreset}
          style={{ minWidth: 200 }}
        >
          应用弱网设置
        </Button>
      </Card>
    </div>
  );
};

export default NetworkSimulate;
