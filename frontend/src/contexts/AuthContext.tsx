import React, { createContext, useContext, useState, ReactNode } from 'react';
import { api } from '../services/api';

// 生成固定唯一的设备 ID（窗口级别）
const getOrCreateDeviceId = (): string => {
  let deviceId = sessionStorage.getItem('device_id');
  if (!deviceId) {
    deviceId = 'win_' + Date.now().toString(36) + Math.random().toString(36).substr(2, 9);
    sessionStorage.setItem('device_id', deviceId);
  }
  return deviceId;
};

interface User {
  username: string;
  roleType: string;
  domainCode: string;
  domainName: string;
  clientId: string;
}

interface AuthContextType {
  user: User | null;
  token: string | null;
  deviceId: string;
  clientId: string; // username_deviceId 格式
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextType>({
  user: null, token: null,
  deviceId: '',
  clientId: '',
  login: async () => {}, logout: () => {},
  isAuthenticated: false,
});

export const useAuth = () => useContext(AuthContext);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const deviceId = getOrCreateDeviceId();
  
  const [user, setUser] = useState<User | null>(() => {
    const saved = sessionStorage.getItem('user');
    return saved ? JSON.parse(saved) : null;
  });
  const [token, setToken] = useState<string | null>(() => sessionStorage.getItem('token'));

  // 计算完整的 clientId: username_deviceId
  const clientId = user ? `${user.username}_${deviceId}` : '';

  const login = async (username: string, password: string) => {
    const result = await api.login({ username, password });
    if (!result.success) throw new Error(result.message);
    const { token: t, username: uname, roleType, domainCode, domainName, clientId } = result.data;
    const u: User = { username: uname, roleType, domainCode, domainName, clientId };
    setToken(t);
    setUser(u);
    sessionStorage.setItem('token', t);
    sessionStorage.setItem('user', JSON.stringify(u));
  };

  const logout = () => {
    setToken(null);
    setUser(null);
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('user');
    // 保留 deviceId，同一窗口刷新时保持一致
  };

  return (
    <AuthContext.Provider value={{ user, token, deviceId, clientId, login, logout, isAuthenticated: !!token }}>
      {children}
    </AuthContext.Provider>
  );
};
