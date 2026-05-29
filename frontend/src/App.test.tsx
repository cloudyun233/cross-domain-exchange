import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const authState = vi.hoisted(() => ({
  authLoading: false,
  isAuthenticated: true,
  profileReady: true,
  user: { username: 'admin', roleType: 'ADMIN', roleName: 'Admin', domainName: 'Root', clientId: 'client-a' } as any,
  logout: vi.fn(),
}));

vi.mock('./contexts/AuthContext', () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => children,
  useAuth: () => authState,
}));

vi.mock('./contexts/PublishContext', () => ({
  PublishProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock('./contexts/SubscribeContext', () => ({
  SubscribeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock('./components/ConnectionStatus', () => ({ default: () => null }));
vi.mock('./pages/Login', () => ({ default: () => 'LoginPage' }));
vi.mock('./pages/Dashboard', () => ({ default: () => 'DashboardPage' }));
vi.mock('./pages/Publish', () => ({ default: () => 'PublishPage' }));
vi.mock('./pages/Subscribe', () => ({ default: () => 'SubscribePage' }));
vi.mock('./pages/DomainManage', () => ({ default: () => 'DomainPage' }));
vi.mock('./pages/ClientManage', () => ({ default: () => 'ClientPage' }));
vi.mock('./pages/AclManage', () => ({ default: () => 'AclPage' }));
vi.mock('./pages/AuditLog', () => ({ default: () => 'AuditPage' }));
vi.mock('./pages/NetworkSimulate', () => ({ default: () => 'NetworkPage' }));

import App from './App';

const adminUser = { username: 'admin', roleType: 'ADMIN', roleName: 'Admin', domainName: 'Root', clientId: 'client-a' };

const LocationProbe = () => {
  const location = useLocation();
  return <div data-testid="path">{location.pathname}</div>;
};

function renderApp(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <LocationProbe />
      <App />
    </MemoryRouter>
  );
}

describe('App routing', () => {
  beforeEach(() => {
    authState.authLoading = false;
    authState.isAuthenticated = true;
    authState.profileReady = true;
    authState.user = adminUser;
    authState.logout.mockClear();
  });

  it('routes unauthenticated users to login', async () => {
    authState.isAuthenticated = false;
    authState.user = null;

    renderApp('/');

    await waitFor(() => expect(screen.getByText('LoginPage')).toBeInTheDocument());
  });

  it('routes admins to the dashboard and keeps admin pages available', async () => {
    authState.isAuthenticated = true;
    authState.profileReady = true;
    authState.user = adminUser;

    renderApp('/');
    await waitFor(() => expect(screen.getByText('DashboardPage')).toBeInTheDocument());

    renderApp('/network');
    await waitFor(() => expect(screen.getByText('NetworkPage')).toBeInTheDocument());
  });

  it('keeps the default route pending while the stored profile is restoring', async () => {
    authState.authLoading = false;
    authState.isAuthenticated = true;
    authState.profileReady = false;
    authState.user = null;

    renderApp('/');

    expect(screen.getByTestId('path')).toHaveTextContent('/');
    await expect(waitFor(() => expect(screen.getByTestId('path')).toHaveTextContent('/publish'), {
      timeout: 100,
    })).rejects.toThrow();
    await expect(waitFor(() => expect(screen.getByText('PublishPage')).toBeInTheDocument(), {
      timeout: 100,
    })).rejects.toThrow();

    authState.profileReady = true;
    authState.user = adminUser;
  });

  it('routes non-admin users away from admin pages', async () => {
    authState.isAuthenticated = true;
    authState.user = { username: 'bob', roleType: 'consumer', roleName: 'Consumer', domainName: 'Root', clientId: 'client-b' };

    renderApp('/dashboard');

    await waitFor(() => expect(screen.getByText('PublishPage')).toBeInTheDocument());
  });
});
