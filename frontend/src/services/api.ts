import { EventSourcePolyfill } from 'event-source-polyfill';

const API_BASE = '/api';

export interface StatusResponse {
  status: string;
}

interface RequestOptions {
  method?: string;
  body?: any;
  params?: Record<string, string>;
  signal?: AbortSignal;
}

async function parseResponseBody<T>(resp: Response): Promise<T> {
  const text = await resp.text();
  if (!text) {
    return undefined as T;
  }

  try {
    return JSON.parse(text) as T;
  } catch {
    throw new Error(text);
  }
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = sessionStorage.getItem('token');
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;

  let url = `${API_BASE}${path}`;
  if (options.params) {
    const qs = new URLSearchParams(options.params).toString();
    url += `?${qs}`;
  }

  const fetchOptions: RequestInit = {
    method: options.method || 'GET',
    headers,
    signal: options.signal,
  };

  if (options.body !== undefined) {
    if (typeof options.body === 'string') {
      headers['Content-Type'] = 'text/plain';
      fetchOptions.body = options.body;
    } else {
      headers['Content-Type'] = 'application/json';
      fetchOptions.body = JSON.stringify(options.body);
    }
  }

  const resp = await fetch(url, fetchOptions);
  if (resp.status === 401) {
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('user');
    window.location.href = '/login';
    throw new Error('认证已过期');
  }

  const data = await parseResponseBody<any>(resp);
  if (!resp.ok) {
    throw new Error(data?.message || `请求失败 (${resp.status})`);
  }

  return data as T;
}

export const api = {
  checkStatus: (signal?: AbortSignal) => request<StatusResponse>('/status/backend', { signal }),
  checkEmqxStatus: (signal?: AbortSignal) => request<StatusResponse>('/status/emqx', { signal }),

  login: (data: { username: string; password: string }) =>
    request<any>('/auth/login', { method: 'POST', body: data }),
  refreshToken: () =>
    request<any>('/auth/refresh', { method: 'POST' }),
  getCurrentUser: () =>
    request<any>('/auth/me'),

  getDomains: () => request<any>('/domains'),
  createDomain: (data: any) => request<any>('/domains', { method: 'POST', body: data }),
  updateDomain: (id: number, data: any) => request<any>(`/domains/${id}`, { method: 'PUT', body: data }),
  deleteDomain: (id: number) => request<any>(`/domains/${id}`, { method: 'DELETE' }),

  getClients: () => request<any>('/clients'),
  createClient: (data: any) => request<any>('/clients', { method: 'POST', body: data }),
  updateClient: (id: number, data: any) => request<any>(`/clients/${id}`, { method: 'PUT', body: data }),
  deleteClient: (id: number) => request<any>(`/clients/${id}`, { method: 'DELETE' }),

  getAclRules: () => request<any>('/acl-rules'),
  createAclRule: (data: any) => request<any>('/acl-rules', { method: 'POST', body: data }),
  updateAclRule: (id: number, data: any) => request<any>(`/acl-rules/${id}`, { method: 'PUT', body: data }),
  deleteAclRule: (id: number) => request<any>(`/acl-rules/${id}`, { method: 'DELETE' }),
  syncAcl: () => request<any>('/acl-rules/sync', { method: 'POST' }),

  getTopicTree: () => request<any>('/topics/tree'),
  publish: (topic: string, payload: string, qos: number, format: string = 'structured') => {
    return request<any>('/topics/publish', {
      method: 'POST',
      body: payload,
      params: { topic, qos: String(qos), format },
    });
  },

  getMetrics: () => request<any>('/monitor/metrics'),
  getMessageStats: () => request<any>('/monitor/message-stats'),
  getClientStats: () => request<any>('/monitor/client-stats'),
  getTopicStats: () => request<any>('/monitor/topic-stats'),
  getConnectionStatus: () => request<any>('/monitor/connection-status'),

  getAuditLogs: (page: number = 1, size: number = 20, clientId?: string, actionType?: string) => {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (clientId) params.clientId = clientId;
    if (actionType) params.actionType = actionType;
    return request<any>('/audit-logs', { params });
  },

  createSubscribeStream: (topic: string, qos: number = 1): EventSourcePolyfill => {
    const token = sessionStorage.getItem('token');
    const url = `${API_BASE}/subscribe/stream?topic=${encodeURIComponent(topic)}&qos=${qos}`;

    return new EventSourcePolyfill(url, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
  },
  cancelSubscribe: (topic: string) => {
    return request<any>('/subscribe/cancel', { method: 'POST', params: { topic } });
  },

  getNetworkPresets: () => request<any>('/network/presets'),
  simulateNetwork: (delay: number, loss: number, bandwidth: number) =>
    request<any>('/network/simulate', {
      method: 'POST',
      params: { delayMs: String(delay), lossPercent: String(loss), bandwidthMbps: String(bandwidth) },
    }),
};
