import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

interface ProtectedRouteProps {
  children: React.ReactNode;
  adminOnly?: boolean;
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, adminOnly = false }) => {
  const { isAuthenticated, user } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (adminOnly && user?.roleType?.toUpperCase() !== 'ADMIN') {
    return <Navigate to="/publish" replace state={{ from: location }} />;
  }

  return <>{children}</>;
};

export default ProtectedRoute;
