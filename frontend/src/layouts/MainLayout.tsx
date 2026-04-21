import React, { useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Avatar, Button, Dropdown, Layout, Menu, Space, Tag, Typography } from 'antd';
import {
  ApartmentOutlined,
  CloudDownloadOutlined,
  DashboardOutlined,
  FileTextOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SafetyOutlined,
  SendOutlined,
  SettingOutlined,
  UserOutlined,
  WifiOutlined,
} from '@ant-design/icons';
import { useAuth } from '../contexts/AuthContext';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const MainLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const isAdmin = user?.roleType?.toUpperCase() === 'ADMIN';

  const commonMenuItems = [
    { key: '/publish', icon: <SendOutlined />, label: '数据发布' },
    { key: '/subscribe', icon: <CloudDownloadOutlined />, label: '数据订阅' },
  ];

  const adminMenuItems = [
    { key: '/dashboard', icon: <DashboardOutlined />, label: '监控大盘' },
    ...commonMenuItems,
    { type: 'divider' as const },
    { key: '/domains', icon: <ApartmentOutlined />, label: '安全域管理' },
    { key: '/clients', icon: <UserOutlined />, label: '用户管理' },
    { key: '/acl', icon: <SafetyOutlined />, label: 'ACL 规则管理' },
    { type: 'divider' as const },
    { key: '/audit', icon: <FileTextOutlined />, label: '审计日志' },
    { key: '/network', icon: <WifiOutlined />, label: '弱网模拟' },
  ];

  const menuItems = isAdmin ? adminMenuItems : commonMenuItems;

  const roleColors: Record<string, string> = {
    admin: 'red',
    producer: 'blue',
    consumer: 'green',
  };

  const normalizedRole = user?.roleType?.toLowerCase() || '';

  const dropdownItems = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: () => {
        logout();
        navigate('/login');
      },
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        theme="light"
        width={240}
        style={{
          background: 'rgba(255, 255, 255, 0.7)',
          backdropFilter: 'blur(20px)',
          WebkitBackdropFilter: 'blur(20px)',
          borderRight: '1px solid rgba(0,0,0,0.05)',
          zIndex: 10,
        }}
      >
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderBottom: '1px solid rgba(0,0,0,0.04)',
          }}
        >
          <SettingOutlined style={{ fontSize: 24, color: '#007AFF' }} />
          {!collapsed && (
            <Text strong style={{ color: '#1D1D1F', marginLeft: 10, fontSize: 16, whiteSpace: 'nowrap', fontWeight: 600, letterSpacing: '-0.3px' }}>
              跨域数据交换系统
            </Text>
          )}
        </div>
        <Menu
          theme="light"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ borderRight: 0, background: 'transparent', padding: '12px 8px' }}
        />
      </Sider>

      <Layout>
        <Header
          style={{
            padding: '0 24px',
            background: 'rgba(255, 255, 255, 0.8)',
            backdropFilter: 'blur(20px)',
            WebkitBackdropFilter: 'blur(20px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: '1px solid rgba(0,0,0,0.05)',
            zIndex: 1,
          }}
        >
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
          />
          <Space size="middle">
            <Tag color={roleColors[normalizedRole] || 'default'}>{user?.roleName || user?.roleType}</Tag>
            <Text type="secondary">所属域: {user?.domainName || '全域'}</Text>
            <Dropdown menu={{ items: dropdownItems }} placement="bottomRight">
              <Space style={{ cursor: 'pointer' }}>
                <Avatar size="small" icon={<UserOutlined />} style={{ background: '#1677ff' }} />
                <Text strong>{user?.username}</Text>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        <Content
          style={{
            margin: '20px 24px',
            padding: 0,
            background: 'transparent',
            minHeight: 280,
            borderRadius: 8,
          }}
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default MainLayout;
