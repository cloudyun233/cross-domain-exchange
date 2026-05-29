import React, {
  ReactNode,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';
import { message } from 'antd';
import { AUTH_UNAUTHORIZED_EVENT, ApiError, api } from '../services/api';

export interface User {
  username: string;
  roleType: string;
  roleName: string;
  domainCode: string;
  domainName: string;
  clientId: string;
}

interface LogoutOptions {
  remote?: boolean;
}

interface StoredSession {
  token: string | null;
  user: User | null;
  profileReady: boolean;
}

interface AuthContextType {
  user: User | null;
  token: string | null;
  login: (username: string, password: string) => Promise<User>;
  logout: (options?: LogoutOptions) => void;
  isAuthenticated: boolean;
  authLoading: boolean;
  profileReady: boolean;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  token: null,
  login: async () => {
    throw new Error('AuthProvider is not mounted');
  },
  logout: () => {},
  isAuthenticated: false,
  authLoading: false,
  profileReady: true,
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

function clearStoredSession() {
  sessionStorage.removeItem('token');
  sessionStorage.removeItem('user');
}

function loadStoredSession(): StoredSession {
  const token = sessionStorage.getItem('token');
  const savedUser = sessionStorage.getItem('user');

  if (!token) {
    clearStoredSession();
    return { token: null, user: null, profileReady: true };
  }

  if (!savedUser) {
    return { token, user: null, profileReady: false };
  }

  try {
    return { token, user: toUser(JSON.parse(savedUser)), profileReady: false };
  } catch {
    clearStoredSession();
    return { token: null, user: null, profileReady: true };
  }
}

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const initialSession = useRef(loadStoredSession()).current;
  const [user, setUser] = useState<User | null>(initialSession.user);
  const [token, setToken] = useState<string | null>(initialSession.token);
  const [profileReady, setProfileReady] = useState(initialSession.profileReady);
  const [authLoading, setAuthLoading] = useState(Boolean(initialSession.token));
  const justLoggedInRef = useRef(false);
  const loggingOutRef = useRef(false);
  const userRef = useRef<User | null>(initialSession.user);

  const persistSession = useCallback((nextToken: string, nextUser: User) => {
    userRef.current = nextUser;
    setUser(nextUser);
    sessionStorage.setItem('token', nextToken);
    sessionStorage.setItem('user', JSON.stringify(nextUser));
    setToken((currentToken) => currentToken === nextToken ? currentToken : nextToken);
  }, []);

  const logout = useCallback((options: LogoutOptions = {}) => {
    const remote = options.remote !== false;
    const hadToken = Boolean(sessionStorage.getItem('token'));

    if (remote && hadToken && !loggingOutRef.current) {
      loggingOutRef.current = true;
      void api.closeSubscribeSession().catch(() => {
        // Session cleanup should not block local logout.
      }).finally(() => {
        loggingOutRef.current = false;
      });
    }

    setToken(null);
    userRef.current = null;
    setUser(null);
    setAuthLoading(false);
    setProfileReady(true);
    clearStoredSession();
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const result = await api.login({ username, password });
    if (!result.success) throw new Error(result.message || 'Login failed');

    const nextUser = toUser(result.data);
    justLoggedInRef.current = true;
    persistSession(result.data.token, nextUser);
    setAuthLoading(false);
    setProfileReady(true);
    return nextUser;
  }, [persistSession]);

  useEffect(() => {
    const handleUnauthorized = (event: Event) => {
      if (loggingOutRef.current) return;
      const detail = (event as CustomEvent<{ message?: string }>).detail;
      message.warning(detail?.message || 'Session expired. Please log in again.');
      logout({ remote: true });
    };

    window.addEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized);
    return () => window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized);
  }, [logout]);

  useEffect(() => {
    if (!token) {
      setAuthLoading(false);
      setProfileReady(true);
      return;
    }

    if (justLoggedInRef.current) {
      justLoggedInRef.current = false;
      setAuthLoading(false);
      setProfileReady(true);
      return;
    }

    let cancelled = false;

    const refreshProfile = async () => {
      setAuthLoading(true);
      setProfileReady(false);
      try {
        const result = await api.getCurrentUser();
        if (cancelled) return;

        if (!result.success) {
          message.warning(result.message || 'Failed to validate session.');
          if (!userRef.current) logout({ remote: false });
          return;
        }

        const nextUser = toUser(result.data);
        const nextToken = result.data.token || token;
        persistSession(nextToken, nextUser);
      } catch (err) {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 401) {
          return;
        }
        message.warning((err as Error)?.message || 'Failed to validate session.');
        if (!userRef.current) logout({ remote: false });
      } finally {
        if (!cancelled) {
          setAuthLoading(false);
          setProfileReady(true);
        }
      }
    };

    void refreshProfile();

    return () => {
      cancelled = true;
    };
  }, [logout, persistSession, token]);

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        login,
        logout,
        isAuthenticated: Boolean(token),
        authLoading,
        profileReady,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
