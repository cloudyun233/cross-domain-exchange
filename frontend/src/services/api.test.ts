import { beforeEach, describe, expect, it, vi } from 'vitest';

const eventSourceInstances: any[] = [];

vi.mock('event-source-polyfill', () => ({
  EventSourcePolyfill: vi.fn().mockImplementation(function EventSourceMock(this: any, url: string, options: any) {
    const instance = { url, options, addEventListener: vi.fn(), close: vi.fn() };
    eventSourceInstances.push(instance);
    return instance;
  }),
}));

import { AUTH_UNAUTHORIZED_EVENT, ApiError, api, request } from './api';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('api request', () => {
  beforeEach(() => {
    eventSourceInstances.length = 0;
    globalThis.fetch = vi.fn();
  });

  it('adds auth headers, query params and JSON request bodies', async () => {
    sessionStorage.setItem('token', 'jwt-token');
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ success: true, data: 1 }));

    const result = await request('/demo', {
      method: 'POST',
      body: { a: 1 },
      params: { q: 'hello world' },
    });

    expect(result).toEqual({ success: true, data: 1 });
    expect(fetch).toHaveBeenCalledWith('/api/demo?q=hello+world', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({
        Authorization: 'Bearer jwt-token',
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ a: 1 }),
    }));
  });

  it('does not attach stale auth headers to login requests', async () => {
    sessionStorage.setItem('token', 'expired-token');
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ success: true, data: { token: 'new-token' } }));

    await api.login({ username: 'u', password: 'p' });

    const options = vi.mocked(fetch).mock.calls[0][1] as RequestInit;
    expect(fetch).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ username: 'u', password: 'p' }),
    }));
    expect(options.headers).toHaveProperty('Content-Type', 'application/json');
    expect(options.headers).not.toHaveProperty('Authorization');
  });

  it('uses text/plain for string payloads and returns text responses', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response('ok', { status: 200 }));

    const result = await request('/plain', { method: 'POST', body: 'payload' });

    expect(result).toBe('ok');
    expect(fetch).toHaveBeenCalledWith('/api/plain', expect.objectContaining({
      headers: expect.objectContaining({ 'Content-Type': 'text/plain' }),
      body: 'payload',
    }));
  });

  it('dispatches global unauthorized events except for login failures', async () => {
    const listener = vi.fn();
    window.addEventListener(AUTH_UNAUTHORIZED_EVENT, listener);
    vi.mocked(fetch)
      .mockResolvedValueOnce(jsonResponse({ message: 'expired', code: 'JWT_EXPIRED' }, 401))
      .mockResolvedValueOnce(jsonResponse({ message: 'bad credentials' }, 401));

    await expect(request('/auth/me')).rejects.toMatchObject({ status: 401, code: 'JWT_EXPIRED' });
    await expect(api.login({ username: 'u', password: 'bad' })).rejects.toBeInstanceOf(ApiError);

    expect(listener).toHaveBeenCalledTimes(1);
    expect(listener.mock.calls[0][0].detail.message).toBe('expired');
    window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, listener);
  });

  it('supports blob responses and parses blob errors', async () => {
    const blob = new Blob(['pdf'], { type: 'application/pdf' });
    vi.mocked(fetch)
      .mockResolvedValueOnce(new Response(blob, { status: 200 }))
      .mockResolvedValueOnce(jsonResponse({ message: 'forbidden' }, 403));

    await expect(request('/file', { responseType: 'blob' })).resolves.toBeInstanceOf(Blob);
    await expect(request('/file', { responseType: 'blob' })).rejects.toMatchObject({
      status: 403,
      message: 'forbidden',
    });
  });

  it('opens SSE with the current token', () => {
    sessionStorage.setItem('token', 'jwt-token');

    const source = api.openSseChannel();

    expect(source).toBe(eventSourceInstances[0]);
    expect(eventSourceInstances[0]).toMatchObject({
      url: '/api/subscribe/sse',
      options: { headers: { Authorization: 'Bearer jwt-token' } },
    });
  });

  it('maps all API helpers to their backend contracts', async () => {
    vi.mocked(fetch).mockImplementation(() =>
      Promise.resolve(jsonResponse({ success: true, data: { records: [], total: 0 } })));

    await api.checkStatus();
    await api.checkEmqxStatus();
    await api.getCurrentUser();
    await api.getDomains();
    await api.getDomainTree();
    await api.createDomain({ domainCode: 'root' });
    await api.updateDomain(3, { domainName: 'Root' });
    await api.deleteDomain(3);
    await api.getClients();
    await api.createClient({ username: 'alice' });
    await api.updateClient(4, { roleType: 'consumer' });
    await api.deleteClient(4);
    await api.getAclRules();
    await api.createAclRule({ username: '*' });
    await api.updateAclRule(5, { action: 'all' });
    await api.deleteAclRule(5);
    await api.syncAcl();
    await api.getTopicTree();
    await api.publish('cross/domain', 'payload', 2, 'text', true);
    await api.getMetrics();
    await api.getMessageStats();
    await api.getClientStats();
    await api.getTopicStats();
    await api.getConnectionStatus();
    await api.getAuditLogs(2, 50, 'client-a', 'acl_deny');
    await api.exportAuditLogsPdf('client-a', 'acl_deny');
    await api.cancelSubscribe('cross/#');
    await api.subscribeToTopic('cross/#', 1);
    await api.getSubscribeSessionStatus();
    await api.connectSubscribeSession();
    await api.disconnectSubscribeSession();
    await api.closeSubscribeSession();
    await api.getNetworkPresets();
    await api.simulateNetwork(100, 5, 10);

    const urls = vi.mocked(fetch).mock.calls.map(([url]) => String(url));
    expect(urls).toEqual(expect.arrayContaining([
      '/api/status/backend',
      '/api/status/emqx',
      '/api/auth/me',
      '/api/domains',
      '/api/domains/tree',
      '/api/clients',
      '/api/acl-rules',
      '/api/topics/tree',
      '/api/monitor/client-stats',
      '/api/monitor/topic-stats',
      '/api/monitor/connection-status',
      '/api/subscribe/session-status',
      '/api/subscribe/connect',
      '/api/subscribe/disconnect',
      '/api/subscribe/close',
      '/api/network/presets',
    ]));
    expect(urls).toContain('/api/audit-logs?page=2&size=50&clientId=client-a&actionType=acl_deny');
    expect(urls).toContain('/api/audit-logs/export/pdf?clientId=client-a&actionType=acl_deny');
    expect(urls).toContain('/api/topics/publish?topic=cross%2Fdomain&qos=2&format=text&retain=true');
    expect(urls).toContain('/api/subscribe/cancel?topic=cross%2F%23');
    expect(urls).toContain('/api/subscribe/topic?topic=cross%2F%23&qos=1');
    expect(urls).toContain('/api/network/simulate?delayMs=100&lossPercent=5&bandwidthMbps=10');
    expect(fetch).toHaveBeenCalledWith('/api/domains/3', expect.objectContaining({ method: 'PUT' }));
    expect(fetch).toHaveBeenCalledWith('/api/clients/4', expect.objectContaining({ method: 'DELETE' }));
    expect(fetch).toHaveBeenCalledWith('/api/acl-rules/sync', expect.objectContaining({ method: 'POST' }));
  });
});
