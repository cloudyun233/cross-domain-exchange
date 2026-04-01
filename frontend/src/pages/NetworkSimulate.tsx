import React, { useEffect, useState } from 'react';
import { Card, Typography, Space, Button, Slider, Tag, Row, Col, Alert, Descriptions, message } from 'antd';
import { ThunderboltOutlined, WifiOutlined } from '@ant-design/icons';
import { api } from '../services/api';

const { Title, Text, Paragraph } = Typography;

interface Preset {
  name: string;
  delay: number;
  loss: number;
  bandwidth: number;
}

const NetworkSimulate: React.FC = () => {
  const [presets, setPresets] = useState<Preset[]>([]);
  const [activePreset, setActivePreset] = useState<string>('标准网络');
  const [delay, setDelay] = useState(0);
  const [loss, setLoss] = useState(0);
  const [bandwidth, setBandwidth] = useState(0);
  const [applying, setApplying] = useState(false);

  useEffect(() => {
    api.getNetworkPresets().then(res => {
      if (res.success) setPresets(res.data);
    });
  }, []);

  const applyPreset = (preset: Preset) => {
    setDelay(preset.delay);
    setLoss(preset.loss);
    setBandwidth(preset.bandwidth);
    setActivePreset(preset.name);
  };

  const handleApply = async () => {
    setApplying(true);
    try {
      const res = await api.simulateNetwork(delay, loss, bandwidth);
      if (res.success) {
        message.success(res.message || '弱网模拟已设置');
      } else {
        message.error(res.message);
      }
    } catch (e: any) {
      message.error('设置失败: ' + e.message);
    } finally {
      setApplying(false);
    }
  };

  return (
    <div>
      <Title level={4}><WifiOutlined /> 弱网模拟（Linux TC）</Title>

      <Alert
        message="说明"
        description="弱网模拟需要在Linux Docker环境中运行,通过Linux TC (traffic control) 模拟跨域网络延迟、丢包和带宽限制。本页面仅在Docker容器化部署时生效。"
        type="info" showIcon style={{ marginBottom: 16 }}
      />

      <Row gutter={16}>
        <Col xs={24} lg={8}>
          <Card title="预设场景 (论文表4-4)" size="small">
            <Space direction="vertical" style={{ width: '100%' }}>
              {presets.map(p => (
                <Button
                  key={p.name}
                  block
                  type={activePreset === p.name ? 'primary' : 'default'}
                  onClick={() => applyPreset(p)}
                  icon={<ThunderboltOutlined />}
                >
                  {p.name}
                </Button>
              ))}
              {presets.length === 0 && (
                <>
                  <Button block onClick={() => applyPreset({ name: '标准网络', delay: 10, loss: 0, bandwidth: 0 })}>
                    标准网络
                  </Button>
                  <Button block onClick={() => applyPreset({ name: '政务跨域波动', delay: 100, loss: 5, bandwidth: 10 })}>
                    政务跨域波动
                  </Button>
                  <Button block onClick={() => applyPreset({ name: '极端弱网', delay: 500, loss: 20, bandwidth: 1 })}>
                    极端弱网
                  </Button>
                </>
              )}
            </Space>
          </Card>
        </Col>

        <Col xs={24} lg={16}>
          <Card title="自定义参数" size="small">
            <Space direction="vertical" style={{ width: '100%' }} size="large">
              <div>
                <Text strong>网络延迟: <Tag color="blue">{delay}ms</Tag></Text>
                <Slider min={0} max={1000} value={delay} onChange={setDelay}
                  marks={{ 0: '0ms', 100: '100ms', 500: '500ms', 1000: '1s' }} />
              </div>
              <div>
                <Text strong>丢包率: <Tag color={loss > 10 ? 'red' : 'green'}>{loss}%</Tag></Text>
                <Slider min={0} max={50} value={loss} onChange={setLoss}
                  marks={{ 0: '0%', 5: '5%', 20: '20%', 50: '50%' }} />
              </div>
              <div>
                <Text strong>带宽限制: <Tag>{bandwidth === 0 ? '无限制' : bandwidth + 'Mbps'}</Tag></Text>
                <Slider min={0} max={100} value={bandwidth} onChange={setBandwidth}
                  marks={{ 0: '无限', 1: '1M', 10: '10M', 100: '100M' }} />
              </div>

              <Descriptions bordered size="small" column={3}>
                <Descriptions.Item label="延迟">{delay}ms</Descriptions.Item>
                <Descriptions.Item label="丢包">{loss}%</Descriptions.Item>
                <Descriptions.Item label="带宽">{bandwidth || '无限制'}Mbps</Descriptions.Item>
              </Descriptions>

              <Button type="primary" block size="large" onClick={handleApply} loading={applying}>
                应用弱网设置
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default NetworkSimulate;
