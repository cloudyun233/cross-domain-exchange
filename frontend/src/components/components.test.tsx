import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import ProtectedRoute from './ProtectedRoute';
import ConnectionStatus from './ConnectionStatus';
import MainLayout from '../layouts/MainLayout';

const authState = vi.hoisted(() => ({
  authLoading: false,
  isAuthenticated: true,
  profileReady: true,
  logout: vi.fn(),
  user: {
    username: 'alice',
    roleType: 'ADMIN',
    roleName: 'Admin',
    domainCode: 'root',
    domainName: 'Root',
    clientId: 'client-a',
  } as any,
}));

const subscribeState = vi.hoisted(() => ({
  mqttConnected: true,
  mqttProtocol: 'TLS',
}));

const apiMock = vi.hoisted(() => ({
  checkStatus: vi.fn(),
  checkEmqxStatus: vi.fn(),
}));

vi.mock('../contexts/AuthContext', () => ({
  useAuth: () => authState,
}));

vi.mock('../contexts/SubscribeContext', () => ({
  useSubscribe: () => subscribeState,
}));

vi.mock('../services/api', () => ({
  api: apiMock,
}));

function renderProtected(path = '/secret', adminOnly = false) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<div>login-page</div>} />
        <Route path="/publish" element={<div>publish-page</div>} />
        <Route path="/secret" element={<ProtectedRoute adminOnly={adminOnly}><div>secret-page</div></ProtectedRoute>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('ProtectedRoute', () => {
  it('waits for auth/profile restoration before deciding', () => {
    authState.authLoading = true;
    authState.isAuthenticated = true;
    authState.profileReady = false;

    const { container } = renderProtected();

    expect(container.querySelector('.ant-spin')).toBeTruthy();
    authState.authLoading = false;
    authState.profileReady = true;
  });

  it('redirects unauthenticated and non-admin users', () => {
    authState.isAuthenticated = false;
    renderProtected();
    expect(screen.getByText('login-page')).toBeInTheDocument();

    authState.isAuthenticated = true;
    authState.user = { ...authState.user, roleType: 'consumer' };
    renderProtected('/secret', true);
    expect(screen.getByText('publish-page')).toBeInTheDocument();

    authState.user = { ...authState.user, roleType: 'ADMIN' };
  });

  it('renders protected content for eligible users', () => {
    authState.isAuthenticated = true;
    authState.profileReady = true;

    renderProtected('/secret', true);

    expect(screen.getByText('secret-page')).toBeInTheDocument();
  });
});

describe('ConnectionStatus', () => {
  it('renders nothing without a logged-in user', () => {
    authState.user = null;

    const { container } = render(<ConnectionStatus />);

    expect(container).toBeEmptyDOMElement();
    authState.user = {
      username: 'alice',
      roleType: 'ADMIN',
      roleName: 'Admin',
      domainCode: 'root',
      domainName: 'Root',
      clientId: 'client-a',
    };
  });

  it('polls backend and EMQX status for logged-in users', async () => {
    apiMock.checkStatus.mockResolvedValue({ status: 'ok' });
    apiMock.checkEmqxStatus.mockResolvedValue({ status: 'online' });

    const { container } = render(<ConnectionStatus />);

    await waitFor(() => expect(apiMock.checkStatus).toHaveBeenCalled());
    expect(container.textContent).toContain('client-a');
    expect(container.textContent).toContain('TLS');
  });

  it('ignores stale request cleanup after a newer request wins', async () => {
    vi.useFakeTimers();
    let rejectFirstBackend!: (error: Error) => void;
    let rejectFirstEmqx!: (error: Error) => void;
    apiMock.checkStatus
      .mockReturnValueOnce(new Promise((_, reject) => { rejectFirstBackend = reject; }))
      .mockResolvedValueOnce({ status: 'ok' });
    apiMock.checkEmqxStatus
      .mockReturnValueOnce(new Promise((_, reject) => { rejectFirstEmqx = reject; }))
      .mockResolvedValueOnce({ status: 'online' });

    const { container } = render(<ConnectionStatus />);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10000);
      await Promise.resolve();
    });
    expect(apiMock.checkStatus).toHaveBeenCalledTimes(2);
    expect(container.textContent).toContain('TLS');

    act(() => {
      rejectFirstBackend(new Error('late backend'));
      rejectFirstEmqx(new Error('late emqx'));
    });

    await act(async () => {
      await Promise.resolve();
    });
    expect(container.textContent).toContain('TLS');
  });
});

describe('MainLayout', () => {
  it('renders admin navigation, supports collapse, navigation and logout', () => {
    authState.user = {
      username: 'alice',
      roleType: 'ADMIN',
      roleName: 'Admin',
      domainCode: 'root',
      domainName: 'Root',
      clientId: 'client-a',
    };

    const { container } = render(
      <MemoryRouter initialEntries={['/publish']}>
        <Routes>
          <Route element={<MainLayout />}>
            <Route path="/publish" element={<div>publish-child</div>} />
            <Route path="/clients" element={<div>clients-child</div>} />
          </Route>
          <Route path="/login" element={<div>login-child</div>} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('publish-child')).toBeInTheDocument();
    fireEvent.click(container.querySelector('header button')!);
    fireEvent.click(container.querySelectorAll('nav button')[4]);
    expect(screen.getByText('clients-child')).toBeInTheDocument();

    fireEvent.click(container.querySelectorAll('header button')[1]);
    expect(authState.logout).toHaveBeenCalled();
    expect(screen.getByText('login-child')).toBeInTheDocument();
  });

  it('keeps non-admin navigation limited to common pages', () => {
    authState.user = {
      username: 'bob',
      roleType: 'consumer',
      roleName: 'Consumer',
      domainCode: 'root',
      domainName: '',
      clientId: 'client-b',
    };

    const { container } = render(
      <MemoryRouter initialEntries={['/publish']}>
        <Routes>
          <Route element={<MainLayout />}>
            <Route path="/publish" element={<div>publish-child</div>} />
            <Route path="/subscribe" element={<div>subscribe-child</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(container.querySelectorAll('nav button')).toHaveLength(2);
    fireEvent.click(container.querySelectorAll('nav button')[1]);
    expect(screen.getByText('subscribe-child')).toBeInTheDocument();
  });
});
