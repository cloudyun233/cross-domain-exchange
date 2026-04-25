import React from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { SubscribeProvider } from './contexts/SubscribeContext';
import { PublishProvider } from './contexts/PublishContext';
import ProtectedRoute from './components/ProtectedRoute';
import MainLayout from './layouts/MainLayout';
import ConnectionStatus from './components/ConnectionStatus';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Publish from './pages/Publish';
import Subscribe from './pages/Subscribe';
import DomainManage from './pages/DomainManage';
import ClientManage from './pages/ClientManage';
import AclManage from './pages/AclManage';
import AuditLog from './pages/AuditLog';
import NetworkSimulate from './pages/NetworkSimulate';

const DefaultRoute: React.FC = () => {
  const { isAuthenticated, user } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <Navigate
      to={user?.roleType?.toUpperCase() === 'ADMIN' ? '/dashboard' : '/publish'}
      replace
    />
  );
};

function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#007AFF',
          borderRadius: 12,
          fontSize: 14,
          fontFamily: `-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', 'Inter', 'Helvetica Neue', Arial, sans-serif`,
          colorBgLayout: '#F5F5F7',
          colorBgContainer: 'rgba(255,255,255,0.78)',
          colorText: '#1D1D1F',
          colorTextSecondary: '#6E6E73',
          colorBorderSecondary: 'rgba(0,0,0,0.06)',
          boxShadowSecondary: '0 10px 34px rgba(18,31,53,0.08)',
          wireframe: false,
        },
        components: {
          Card: {
            paddingLG: 20,
            borderRadiusLG: 20,
            headerFontSize: 15,
          },
          Button: {
            borderRadius: 999,
            controlHeight: 38,
            fontWeight: 650,
          },
          Input: {
            controlHeight: 38,
            borderRadius: 12,
          },
          Select: {
            controlHeight: 38,
            borderRadius: 12,
          },
          Table: {
            headerBg: 'rgba(246,248,251,0.82)',
            headerColor: '#86868B',
            rowHoverBg: 'rgba(0,122,255,0.045)',
          },
          Modal: {
            borderRadiusLG: 22,
          },
          Alert: {
            borderRadiusLG: 16,
          },
          Tag: {
            borderRadiusSM: 999,
          },
        }
      }}
    >
      <AuthProvider>
        <PublishProvider>
          <SubscribeProvider>
            <ConnectionStatus />
            <Routes>
            <Route path="/login" element={<Login />} />
            <Route
              element={
                <ProtectedRoute>
                  <MainLayout />
                </ProtectedRoute>
              }
            >
              <Route path="/publish" element={<Publish />} />
              <Route path="/subscribe" element={<Subscribe />} />
              <Route
                path="/dashboard"
                element={
                  <ProtectedRoute adminOnly>
                    <Dashboard />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/domains"
                element={
                  <ProtectedRoute adminOnly>
                    <DomainManage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/clients"
                element={
                  <ProtectedRoute adminOnly>
                    <ClientManage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/acl"
                element={
                  <ProtectedRoute adminOnly>
                    <AclManage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/audit"
                element={
                  <ProtectedRoute adminOnly>
                    <AuditLog />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/network"
                element={
                  <ProtectedRoute adminOnly>
                    <NetworkSimulate />
                  </ProtectedRoute>
                }
              />
            </Route>
            <Route path="/" element={<DefaultRoute />} />
            <Route path="*" element={<DefaultRoute />} />
          </Routes>
          </SubscribeProvider>
        </PublishProvider>
      </AuthProvider>
    </ConfigProvider>
  );
}

export default App;
