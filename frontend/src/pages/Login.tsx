import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Form, Input, Space, Typography, message } from 'antd';
import { LockOutlined, SafetyOutlined, UserOutlined } from '@ant-design/icons';
import { useAuth } from '../contexts/AuthContext';

const { Title, Text, Paragraph } = Typography;

const Login: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      await login(values.username, values.password);
      const savedUser = sessionStorage.getItem('user');
      const roleType = savedUser ? JSON.parse(savedUser).roleType : '';
      message.success('登录成功，登录态仅在当前标签页有效');
      navigate(roleType?.toUpperCase() === 'ADMIN' ? '/dashboard' : '/publish');
    } catch (err: any) {
      message.error(err.message || '登录失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-bg">
      <Card
        className="login-card"
        styles={{ body: { padding: 40 } }}
      >
        <Space direction="vertical" size="large" style={{ width: '100%', textAlign: 'center' }}>
          <span className="login-mark">
            <SafetyOutlined style={{ fontSize: 38 }} />
          </span>
          <div>
            <Title level={3} style={{ margin: 0, fontWeight: 700, letterSpacing: '-0.5px' }}>跨域数据交换系统</Title>
            <Text type="secondary" style={{ marginTop: 8, display: 'block', fontSize: 15 }}>基于发布订阅机制的安全数据交换平台</Text>
          </div>
        </Space>

        <Form name="login" onFinish={onFinish} size="large" style={{ marginTop: 32 }}>
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="用户名 / ClientID" autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码" autoComplete="current-password" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block loading={loading}>
              登录
            </Button>
          </Form.Item>
        </Form>

        <Card size="small" className="ios-group" style={{ marginTop: 8 }}>
          <Text strong style={{ color: '#1D1D1F' }}>演示账号：</Text>
          <Paragraph style={{ margin: '4px 0 0', fontSize: 12 }} type="secondary">
            管理员：admin / admin123<br />
            生产者（医疗域 / 西南医院）：producer_medical_swh / 123456<br />
            消费者（政务域）：consumer_gov / 123456<br />
            消费者（医疗域 / 西南医院）：consumer_medical_swh / 123456
          </Paragraph>
        </Card>
      </Card>
    </div>
  );
};

export default Login;
