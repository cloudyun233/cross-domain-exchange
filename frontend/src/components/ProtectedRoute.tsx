import React from 'react';
import { Spin } from 'antd';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

interface ProtectedRouteProps {
  children: React.ReactNode;
  adminOnly?: boolean;
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, adminOnly = false }) => {
  const { authLoading, isAuthenticated, profileReady, user } = useAuth();
  const location = useLocation();

  if (authLoading || (isAuthenticated && !profileReady)) {
    return (
      <div style={{ minHeight: 240, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Spin />
      </div>
    );
  }

  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location }} />;
  if (adminOnly && user?.roleType?.toUpperCase() !== 'ADMIN') {
    return <Navigate to="/publish" replace state={{ from: location }} />;
  }

  return <>{children}</>;
};

export default ProtectedRoute;
