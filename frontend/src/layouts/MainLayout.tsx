import React, { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Button, Tag, Typography, Space, theme, Avatar, Dropdown } from 'antd';
import {
  DashboardOutlined, SendOutlined, CloudDownloadOutlined,
  ApartmentOutlined, UserOutlined, SafetyOutlined,
  FileTextOutlined, WifiOutlined, LogoutOutlined,
  MenuFoldOutlined, MenuUnfoldOutlined, SettingOutlined,
} from '@ant-design/icons';
import { useAuth } from '../contexts/AuthContext';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const MainLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { token: themeToken } = theme.useToken();

  const menuItems = [
    { key: '/dashboard', icon: <DashboardOutlined />, label: '监控大盘' },
    { key: '/publish', icon: <SendOutlined />, label: '数据发布' },
    { key: '/subscribe', icon: <CloudDownloadOutlined />, label: '数据订阅' },
    { type: 'divider' as const },
    { key: '/domains', icon: <ApartmentOutlined />, label: '安全域管理' },
    { key: '/clients', icon: <UserOutlined />, label: '客户端管理' },
    { key: '/acl', icon: <SafetyOutlined />, label: 'ACL规则管理' },
    { type: 'divider' as const },
    { key: '/audit', icon: <FileTextOutlined />, label: '审计日志' },
    { key: '/network', icon: <WifiOutlined />, label: '弱网模拟' },
  ];

  const roleColors: Record<string, string> = {
    admin: 'red', producer: 'blue', consumer: 'green',
  };

  const roleLabels: Record<string, string> = {
    admin: '管理员', producer: '生产者', consumer: '消费者',
  };

  const dropdownItems = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: () => { logout(); navigate('/login'); },
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        theme="dark"
        style={{
          background: 'linear-gradient(180deg, #001529 0%, #002140 100%)',
          boxShadow: '2px 0 8px rgba(0,0,0,0.15)',
        }}
      >
        <div style={{
          height: 64, display: 'flex', alignItems: 'center', justifyContent: 'center',
          borderBottom: '1px solid rgba(255,255,255,0.1)',
        }}>
          <SettingOutlined style={{ fontSize: 24, color: '#1890ff' }} />
          {!collapsed && (
            <Text strong style={{ color: '#fff', marginLeft: 10, fontSize: 14, whiteSpace: 'nowrap' }}>
              跨域数据交换系统
            </Text>
          )}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ borderRight: 0, background: 'transparent' }}
        />
      </Sider>

      <Layout>
        <Header style={{
          padding: '0 24px', background: '#fff',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          boxShadow: '0 1px 4px rgba(0,0,0,0.08)', zIndex: 1,
        }}>
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
          />
          <Space size="middle">
            <Tag color={roleColors[user?.roleType || ''] || 'default'}>
              {roleLabels[user?.roleType || ''] || user?.roleType}
            </Tag>
            <Text type="secondary">域: {user?.domainName || user?.domainCode}</Text>
            <Dropdown menu={{ items: dropdownItems }} placement="bottomRight">
              <Space style={{ cursor: 'pointer' }}>
                <Avatar size="small" icon={<UserOutlined />} style={{ background: '#1890ff' }} />
                <Text strong>{user?.clientId}</Text>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        <Content style={{
          margin: 16, padding: 20,
          background: '#f0f2f5', minHeight: 280, borderRadius: 8,
        }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default MainLayout;
