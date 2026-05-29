import React from 'react';
import { fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { message } from 'antd';

const apiMock = vi.hoisted(() => ({
  login: vi.fn(),
  getCurrentUser: vi.fn(),
  closeSubscribeSession: vi.fn(),
}));

vi.mock('../services/api', () => {
  class ApiError extends Error {
    status: number;
    code?: string;

    constructor(message: string, status: number, code?: string) {
      super(message);
      this.status = status;
      this.code = code;
    }
  }

  return {
    AUTH_UNAUTHORIZED_EVENT: 'cde:auth-unauthorized',
    ApiError,
    api: apiMock,
  };
});

import { AuthProvider, useAuth } from './AuthContext';
import { ApiError } from '../services/api';

const userPayload = {
  username: 'alice',
  roleType: 'admin',
  roleName: 'Admin',
  domainCode: 'root',
  domainName: 'Root',
  clientId: 'client-a',
};

const Probe = () => {
  const auth = useAuth();
  return (
    <div>
      <div data-testid="username">{auth.user?.username || ''}</div>
      <div data-testid="token">{auth.token || ''}</div>
      <div data-testid="loading">{String(auth.authLoading)}</div>
      <div data-testid="ready">{String(auth.profileReady)}</div>
      <button onClick={() => void auth.login('alice', 'pw')}>login</button>
      <button onClick={() => auth.logout()}>logout</button>
    </div>
  );
};

function renderAuth() {
  return render(<AuthProvider><Probe /></AuthProvider>);
}

describe('AuthContext', () => {
  it('exposes a defensive default login when no provider is mounted', async () => {
    const { result } = renderHook(() => useAuth());

    await expect(result.current.login('alice', 'pw')).rejects.toThrow('AuthProvider is not mounted');
  });

  it('clears damaged sessionStorage instead of crashing', () => {
    sessionStorage.setItem('token', 'broken-token');
    sessionStorage.setItem('user', '{broken');

    renderAuth();

    expect(screen.getByTestId('token')).toHaveTextContent('');
    expect(sessionStorage.getItem('token')).toBeNull();
    expect(sessionStorage.getItem('user')).toBeNull();
    expect(screen.getByTestId('ready')).toHaveTextContent('true');
  });

  it('normalizes login responses, persists them and returns a ready profile', async () => {
    apiMock.login.mockResolvedValueOnce({ success: true, data: { ...userPayload, token: 'new-token' } });

    renderAuth();
    fireEvent.click(screen.getByText('login'));

    await waitFor(() => expect(screen.getByTestId('username')).toHaveTextContent('alice'));
    expect(screen.getByTestId('token')).toHaveTextContent('new-token');
    expect(screen.getByTestId('loading')).toHaveTextContent('false');
    expect(screen.getByTestId('ready')).toHaveTextContent('true');
    expect(JSON.parse(sessionStorage.getItem('user') || '{}')).toMatchObject({ username: 'alice' });
  });

  it('refreshes a stored token before admin routing decisions are made', async () => {
    sessionStorage.setItem('token', 'old-token');
    sessionStorage.setItem('user', JSON.stringify({ ...userPayload, username: 'cached' }));
    apiMock.getCurrentUser.mockResolvedValueOnce({
      success: true,
      data: { ...userPayload, username: 'fresh', token: 'fresh-token' },
    });

    renderAuth();

    expect(screen.getByTestId('ready')).toHaveTextContent('false');
    await waitFor(() => expect(screen.getByTestId('username')).toHaveTextContent('fresh'));
    expect(sessionStorage.getItem('token')).toBe('fresh-token');
    expect(screen.getByTestId('ready')).toHaveTextContent('true');
  });

  it('handles global unauthorized events through the shared logout path', async () => {
    sessionStorage.setItem('token', 'old-token');
    sessionStorage.setItem('user', JSON.stringify(userPayload));
    apiMock.getCurrentUser.mockResolvedValueOnce({ success: true, data: { ...userPayload, token: 'old-token' } });
    apiMock.closeSubscribeSession.mockResolvedValueOnce({ success: true });

    renderAuth();
    await waitFor(() => expect(screen.getByTestId('ready')).toHaveTextContent('true'));

    window.dispatchEvent(new CustomEvent('cde:auth-unauthorized', { detail: { message: 'expired' } }));

    await waitFor(() => expect(screen.getByTestId('token')).toHaveTextContent(''));
    expect(apiMock.closeSubscribeSession).toHaveBeenCalledTimes(1);
    expect(message.warning).toHaveBeenCalledWith('expired');
  });

  it('warns and clears an unhydrated session when profile refresh returns failure', async () => {
    sessionStorage.setItem('token', 'old-token');
    apiMock.getCurrentUser.mockResolvedValueOnce({ success: false, message: 'invalid session' });

    renderAuth();

    await waitFor(() => expect(screen.getByTestId('token')).toHaveTextContent(''));
    expect(message.warning).toHaveBeenCalledWith('invalid session');
    expect(apiMock.closeSubscribeSession).not.toHaveBeenCalled();
  });

  it('leaves explicit 401 profile refresh handling to the global unauthorized flow', async () => {
    sessionStorage.setItem('token', 'old-token');
    apiMock.getCurrentUser.mockRejectedValueOnce(new ApiError('expired', 401));

    renderAuth();

    await waitFor(() => expect(screen.getByTestId('ready')).toHaveTextContent('true'));
    expect(message.warning).not.toHaveBeenCalledWith('expired');
  });

  it('does not remote logout for explicit local logout when no token exists', () => {
    renderAuth();

    fireEvent.click(screen.getByText('logout'));

    expect(apiMock.closeSubscribeSession).not.toHaveBeenCalled();
    expect(screen.getByTestId('ready')).toHaveTextContent('true');
  });
});
