import { EventSourcePolyfill } from 'event-source-polyfill';

const API_BASE = '/api';

export const AUTH_UNAUTHORIZED_EVENT = 'cde:auth-unauthorized';

export interface StatusResponse {
  status: string;
}

interface RequestOptions {
  method?: string;
  body?: any;
  params?: Record<string, string>;
  signal?: AbortSignal;
  responseType?: 'json' | 'blob';
}

export class ApiError extends Error {
  status: number;
  code?: string;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

async function parseResponseBody<T>(resp: Response): Promise<T | undefined> {
  const text = await resp.text();
  if (!text) return undefined;

  try {
    return JSON.parse(text) as T;
  } catch {
    return text as T;
  }
}

function buildUrl(path: string, params?: Record<string, string>) {
  let url = `${API_BASE}${path}`;
  if (params) {
    const qs = new URLSearchParams(params).toString();
    if (qs) url += `?${qs}`;
  }
  return url;
}

function dispatchUnauthorized(message: string) {
  window.dispatchEvent(new CustomEvent(AUTH_UNAUTHORIZED_EVENT, { detail: { message } }));
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const isLoginRequest = path === '/auth/login';
  const token = sessionStorage.getItem('token');
  const headers: Record<string, string> = {};
  if (token && !isLoginRequest) headers.Authorization = `Bearer ${token}`;

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

  const resp = await fetch(buildUrl(path, options.params), fetchOptions);

  if (options.responseType === 'blob') {
    if (resp.ok) return await resp.blob() as T;
    const errorBody = await parseResponseBody<any>(resp);
    const message = typeof errorBody === 'string' ? errorBody : errorBody?.message;
    if (resp.status === 401 && !isLoginRequest) dispatchUnauthorized(message || 'Session expired');
    throw new ApiError(message || `Request failed (${resp.status})`, resp.status, errorBody?.code);
  }

  const data = await parseResponseBody<any>(resp);
  if (!resp.ok) {
    const message = typeof data === 'string' ? data : data?.message;
    if (resp.status === 401 && !isLoginRequest) dispatchUnauthorized(message || 'Session expired');
    throw new ApiError(message || `Request failed (${resp.status})`, resp.status, data?.code);
  }

  return data as T;
}

export const api = {
  checkStatus: (signal?: AbortSignal) => request<StatusResponse>('/status/backend', { signal }),
  checkEmqxStatus: (signal?: AbortSignal) => request<StatusResponse>('/status/emqx', { signal }),

  login: (data: { username: string; password: string }) =>
    request<any>('/auth/login', { method: 'POST', body: data }),
  getCurrentUser: () => request<any>('/auth/me'),

  getDomains: () => request<any>('/domains'),
  getDomainTree: () => request<any>('/domains/tree'),
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
  publish: (topic: string, payload: string, qos: number, format: string = 'structured', retain: boolean = false) =>
    request<any>('/topics/publish', {
      method: 'POST',
      body: payload,
      params: { topic, qos: String(qos), format, retain: String(retain) },
    }),

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
  exportAuditLogsPdf: (clientId?: string, actionType?: string) => {
    const params: Record<string, string> = {};
    if (clientId) params.clientId = clientId;
    if (actionType) params.actionType = actionType;
    return request<Blob>('/audit-logs/export/pdf', { params, responseType: 'blob' });
  },

  openSseChannel: (): EventSourcePolyfill => {
    const token = sessionStorage.getItem('token');
    return new EventSourcePolyfill(`${API_BASE}/subscribe/sse`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
  },

  cancelSubscribe: (topic: string) =>
    request<any>('/subscribe/cancel', { method: 'POST', params: { topic } }),
  subscribeToTopic: (topic: string, qos: number) =>
    request<any>('/subscribe/topic', { method: 'POST', params: { topic, qos: String(qos) } }),
  getSubscribeSessionStatus: () => request<any>('/subscribe/session-status'),
  connectSubscribeSession: () => request<any>('/subscribe/connect', { method: 'POST' }),
  disconnectSubscribeSession: () => request<any>('/subscribe/disconnect', { method: 'POST' }),
  closeSubscribeSession: () => request<any>('/subscribe/close', { method: 'POST' }),

  getNetworkPresets: () => request<any>('/network/presets'),
  simulateNetwork: (delay: number, loss: number, bandwidth: number) =>
    request<any>('/network/simulate', {
      method: 'POST',
      params: { delayMs: String(delay), lossPercent: String(loss), bandwidthMbps: String(bandwidth) },
    }),
};
