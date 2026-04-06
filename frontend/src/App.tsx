import React from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { SubscribeProvider } from './contexts/SubscribeContext';
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
          colorPrimary: '#1890ff',
          borderRadius: 6,
          fontSize: 14,
        },
      }}
    >
      <AuthProvider>
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
      </AuthProvider>
    </ConfigProvider>
  );
}

export default App;
