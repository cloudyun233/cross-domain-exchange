import React, { ReactNode, createContext, useContext, useEffect, useRef, useState } from 'react';
import { api } from '../services/api';

interface User {
  username: string;
  roleType: string;
  roleName: string;
  domainCode: string;
  domainName: string;
  clientId: string;
}

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

    if (justLoggedInRef.current) {
      justLoggedInRef.current = false;
      return;
    }

    let cancelled = false;

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
