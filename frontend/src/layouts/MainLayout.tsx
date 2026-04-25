import React, { useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Button, Dropdown, Layout, Menu, Space, Tag, Typography } from 'antd';
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
    <Layout className="app-shell">
      <Sider
        className="app-sider"
        trigger={null}
        collapsible
        collapsed={collapsed}
        theme="light"
        width={250}
      >
        <div className="brand-panel">
          <span className="brand-icon">
            <SettingOutlined style={{ fontSize: 22 }} />
          </span>
          {!collapsed && (
            <Text className="brand-title">
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
          style={{ padding: '12px 0' }}
        />
      </Sider>

      <Layout>
        <Header className="app-header">
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
          />
          <Space className="header-actions" size="small" wrap>
            <Tag color={roleColors[normalizedRole] || 'default'}>{user?.roleName || user?.roleType}</Tag>
            <Tag color="blue">{user?.domainName || '全域'}</Tag>
            <Dropdown menu={{ items: dropdownItems }} placement="bottomRight">
              <Space className="header-profile" style={{ cursor: 'pointer' }}>
                <span className="header-avatar"><UserOutlined /></span>
                <Text strong className="header-username">{user?.username}</Text>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        <Content className="page-container">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default MainLayout;
