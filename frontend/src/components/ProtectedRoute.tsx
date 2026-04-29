/**
 * 路由守卫组件 —— 控制页面访问权限
 *
 * 行为说明：
 * - 未登录用户 → 重定向到 /login
 * - adminOnly=true 且当前用户非管理员 → 重定向到 /publish（普通用户首页）
 * - 通过守卫则渲染子组件
 */
import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

/** 路由守卫属性 */
interface ProtectedRouteProps {
  /** 子组件 */
  children: React.ReactNode;
  /** 是否仅限管理员访问，默认 false */
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
