/**
 * 统一 API 客户端 —— JWT 认证、401 自动重定向、SSE 通道支持
 *
 * 设计要点：
 * - 所有请求自动注入 Authorization: Bearer {token} 头
 * - 401 响应自动清除 sessionStorage 并跳转 /login，无需各页面单独处理
 * - 支持 JSON / 纯文本 / Blob 三种响应类型
 * - SSE 通道使用 EventSourcePolyfill 实现，支持自定义请求头
 */
import { EventSourcePolyfill } from 'event-source-polyfill';

/** API 基础路径，通过 Vite proxy 代理到后端 */
const API_BASE = '/api';

/** 健康检查响应结构 */
export interface StatusResponse {
  status: string;
}

/** 通用请求选项 */
interface RequestOptions {
  method?: string;
  body?: any;
  params?: Record<string, string>;
  signal?: AbortSignal;
  responseType?: 'json' | 'blob';
}

/**
 * 解析响应体：
 * - 空响应返回 undefined
 * - 尝试 JSON 解析，失败则将原始文本作为错误消息抛出
 */
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

/**
 * 统一请求函数：
 * 1. 从 sessionStorage 读取 token 并注入 Authorization 头
 * 2. 根据 body 类型自动设置 Content-Type（string → text/plain, object → application/json）
 * 3. 401 响应自动清除会话并跳转登录页
 * 4. 支持 blob 响应类型（用于 PDF 导出等场景）
 * 5. 非 2xx 响应解析错误消息后抛出异常
 */
async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = sessionStorage.getItem('token');
  const headers: Record<string, string> = {};
  if (token) headers.Authorization = `Bearer ${token}`;

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

  if (options.responseType === 'blob') {
    if (!resp.ok) {
      const text = await resp.text();
      throw new Error(text || `请求失败 (${resp.status})`);
    }
    return await resp.blob() as T;
  }

  const data = await parseResponseBody<any>(resp);
  if (!resp.ok) {
    throw new Error(data?.message || `请求失败 (${resp.status})`);
  }

  return data as T;
}

export const api = {
  // ── 健康检查 ──────────────────────────────────────────────────────────────
  checkStatus: (signal?: AbortSignal) => request<StatusResponse>('/status/backend', { signal }),
  checkEmqxStatus: (signal?: AbortSignal) => request<StatusResponse>('/status/emqx', { signal }),

  // ── 认证 ──────────────────────────────────────────────────────────────────
  login: (data: { username: string; password: string }) =>
    request<any>('/auth/login', { method: 'POST', body: data }),
  refreshToken: () => request<any>('/auth/refresh', { method: 'POST' }),
  getCurrentUser: () => request<any>('/auth/me'),

  // ── 安全域 ─────────────────────────────────────────────────────────────────
  getDomains: () => request<any>('/domains'),
  getDomainTree: () => request<any>('/domains/tree'),
  createDomain: (data: any) => request<any>('/domains', { method: 'POST', body: data }),
  updateDomain: (id: number, data: any) => request<any>(`/domains/${id}`, { method: 'PUT', body: data }),
  deleteDomain: (id: number) => request<any>(`/domains/${id}`, { method: 'DELETE' }),

  // ── 用户/客户端 ────────────────────────────────────────────────────────────
  getClients: () => request<any>('/clients'),
  createClient: (data: any) => request<any>('/clients', { method: 'POST', body: data }),
  updateClient: (id: number, data: any) => request<any>(`/clients/${id}`, { method: 'PUT', body: data }),
  deleteClient: (id: number) => request<any>(`/clients/${id}`, { method: 'DELETE' }),

  // ── ACL 规则 ──────────────────────────────────────────────────────────────
  getAclRules: () => request<any>('/acl-rules'),
  createAclRule: (data: any) => request<any>('/acl-rules', { method: 'POST', body: data }),
  updateAclRule: (id: number, data: any) => request<any>(`/acl-rules/${id}`, { method: 'PUT', body: data }),
  deleteAclRule: (id: number) => request<any>(`/acl-rules/${id}`, { method: 'DELETE' }),
  syncAcl: () => request<any>('/acl-rules/sync', { method: 'POST' }),

  // ── 主题与发布 ────────────────────────────────────────────────────────────
  getTopicTree: () => request<any>('/topics/tree'),
  publish: (topic: string, payload: string, qos: number, format: string = 'structured', retain: boolean = false) =>
    request<any>('/topics/publish', {
      method: 'POST',
      body: payload,
      params: { topic, qos: String(qos), format, retain: String(retain) },
    }),

  // ── 监控 ──────────────────────────────────────────────────────────────────
  getMetrics: () => request<any>('/monitor/metrics'),
  getMessageStats: () => request<any>('/monitor/message-stats'),
  getClientStats: () => request<any>('/monitor/client-stats'),
  getTopicStats: () => request<any>('/monitor/topic-stats'),
  getConnectionStatus: () => request<any>('/monitor/connection-status'),

  // ── 审计日志 ──────────────────────────────────────────────────────────────
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

  /**
   * 建立 SSE 长连接通道：
   * - 使用 EventSourcePolyfill 支持自定义 Authorization 头
   * - 原生 EventSource 不支持自定义头，故使用 polyfill
   * - 前端进入订阅页时立即调用，保持直到退出登录
   */
  openSseChannel: (): EventSourcePolyfill => {
    const token = sessionStorage.getItem('token');
    const url = `${API_BASE}/subscribe/sse`;
    return new EventSourcePolyfill(url, {
      headers: { Authorization: `Bearer ${token}` },
    });
  },

  // ── 订阅会话 ──────────────────────────────────────────────────────────────
  cancelSubscribe: (topic: string) =>
    request<any>('/subscribe/cancel', { method: 'POST', params: { topic } }),
  /** 在已有 MQTT + SSE 的情况下新增订阅主题 */
  subscribeToTopic: (topic: string, qos: number) =>
    request<any>('/subscribe/topic', { method: 'POST', params: { topic, qos: String(qos) } }),
  getSubscribeSessionStatus: () => request<any>('/subscribe/session-status'),
  /** 连接 MQTT（不重建 SSE），cleanStart=false，触发持久会话离线消息补发 */
  connectSubscribeSession: () => request<any>('/subscribe/connect', { method: 'POST' }),
  /** 仅断开 MQTT，SSE 保持，EMQX 开始缓存离线消息 */
  disconnectSubscribeSession: () => request<any>('/subscribe/disconnect', { method: 'POST' }),
  /** 完全关闭：MQTT + SSE 全部断开（退出登录时） */
  closeSubscribeSession: () => request<any>('/subscribe/close', { method: 'POST' }),

  // ── 弱网模拟 ──────────────────────────────────────────────────────────────
  getNetworkPresets: () => request<any>('/network/presets'),
  simulateNetwork: (delay: number, loss: number, bandwidth: number) =>
    request<any>('/network/simulate', {
      method: 'POST',
      params: { delayMs: String(delay), lossPercent: String(loss), bandwidthMbps: String(bandwidth) },
    }),
};
