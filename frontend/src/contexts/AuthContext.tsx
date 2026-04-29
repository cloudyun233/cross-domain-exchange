/**
 * 认证上下文 —— 管理用户登录态、JWT Token 与会话持久化
 *
 * 核心设计：
 * - 使用 sessionStorage 持久化 token 和 user，刷新页面不丢失但关闭标签页即失效
 * - 登录成功后通过 toUser() 将后端响应映射为前端 User 结构
 * - token 变化时自动调用 /auth/me 刷新用户信息（跳过刚登录的那次，避免重复请求）
 * - 退出时清理 sessionStorage 并通知后端关闭订阅会话
 */
import React, { ReactNode, createContext, useContext, useEffect, useRef, useState } from 'react';
import { api } from '../services/api';

/** 用户信息结构，对应后端 /auth/me 返回的用户字段 */
interface User {
  username: string;
  roleType: string;
  roleName: string;
  domainCode: string;
  domainName: string;
  clientId: string;
}

/** 认证上下文接口 */
interface AuthContextType {
  user: User | null;
  token: string | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  token: null,
  login: async () => {},
  logout: () => {},
  isAuthenticated: false,
});

export const useAuth = () => useContext(AuthContext);

/**
 * 将后端返回的用户数据映射为前端 User 结构
 * 后端返回的 payload 包含额外字段（如 token），此函数仅提取 UI 层所需字段
 */
function toUser(payload: any): User {
  return {
    username: payload.username,
    roleType: payload.roleType,
    roleName: payload.roleName,
    domainCode: payload.domainCode,
    domainName: payload.domainName,
    clientId: payload.clientId,
  };
}

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(() => {
    const saved = sessionStorage.getItem('user');
    return saved ? JSON.parse(saved) : null;
  });
  const [token, setToken] = useState<string | null>(() => sessionStorage.getItem('token'));
  const justLoggedInRef = useRef(false);

  /** 将 token 和 user 同步写入 state 与 sessionStorage */
  const persistSession = (nextToken: string, nextUser: User) => {
    setToken(nextToken);
    setUser(nextUser);
    sessionStorage.setItem('token', nextToken);
    sessionStorage.setItem('user', JSON.stringify(nextUser));
  };

  const login = async (username: string, password: string) => {
    const result = await api.login({ username, password });
    if (!result.success) throw new Error(result.message);

    const nextUser = toUser(result.data);
    justLoggedInRef.current = true;
    persistSession(result.data.token, nextUser);
  };

  const logout = () => {
    void api.closeSubscribeSession().catch(() => {
      // ignore logout cleanup errors
    });
    setToken(null);
    setUser(null);
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('user');
  };

  useEffect(() => {
    if (!token) {
      return;
    }

    // 刚登录时跳过刷新，因为 login() 已经拿到了最新用户信息
    if (justLoggedInRef.current) {
      justLoggedInRef.current = false;
      return;
    }

    let cancelled = false;

    // token 变化时（如页面刷新从 sessionStorage 恢复），向后端请求最新用户信息
    const refreshProfile = async () => {
      try {
        const result = await api.getCurrentUser();
        if (!result.success || cancelled) {
          return;
        }

        const nextUser = toUser(result.data);
        const nextToken = result.data.token || token;
        persistSession(nextToken, nextUser);
      } catch {
        if (!cancelled) {
        }
      }
    };

    void refreshProfile();

    return () => {
      cancelled = true;
    };
  }, [token]);

  return (
    <AuthContext.Provider value={{ user, token, login, logout, isAuthenticated: !!token }}>
      {children}
    </AuthContext.Provider>
  );
};
