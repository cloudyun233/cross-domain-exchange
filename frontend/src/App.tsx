import { Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { AuthProvider } from './contexts/AuthContext';
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
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/publish" element={<Publish />} />
            <Route path="/subscribe" element={<Subscribe />} />
            <Route path="/domains" element={<DomainManage />} />
            <Route path="/clients" element={<ClientManage />} />
            <Route path="/acl" element={<AclManage />} />
            <Route path="/audit" element={<AuditLog />} />
            <Route path="/network" element={<NetworkSimulate />} />
          </Route>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </AuthProvider>
    </ConfigProvider>
  );
}

export default App;
