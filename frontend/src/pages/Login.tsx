import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Form, Input, Button, Typography, message, Space } from 'antd';
import { UserOutlined, LockOutlined, SafetyOutlined } from '@ant-design/icons';
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
      message.success('登录成功');
      navigate('/dashboard');
    } catch (err: any) {
      message.error(err.message || '登录失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-bg">
      <Card
        style={{
          width: 420, borderRadius: 12,
          boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
        }}
        styles={{ body: { padding: 40 } }}
      >
        <Space direction="vertical" size="large" style={{ width: '100%', textAlign: 'center' }}>
          <SafetyOutlined style={{ fontSize: 48, color: '#1890ff' }} />
          <Title level={3} style={{ margin: 0 }}>跨域数据交换系统</Title>
          <Text type="secondary">基于发布订阅机制的安全数据交换平台</Text>
        </Space>

        <Form
          name="login"
          onFinish={onFinish}
          size="large"
          style={{ marginTop: 32 }}
        >
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="用户名 / ClientID" autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码" autoComplete="current-password" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block loading={loading}>
              登 录
            </Button>
          </Form.Item>
        </Form>

        <Card size="small" style={{ background: '#f6f8fa', marginTop: 8 }}>
          <Text strong>演示账号：</Text>
          <Paragraph style={{ margin: '4px 0 0', fontSize: 12 }} type="secondary">
            管理员: admin / admin123<br />
            生产者(医疗域): producer_swu / 123456<br />
            消费者(政务域): consumer_social / 123456<br />
            消费者(企业域): consumer_c / 123456
          </Paragraph>
        </Card>
      </Card>
    </div>
  );
};

export default Login;
