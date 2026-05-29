import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { message } from 'antd';

const apiMock = vi.hoisted(() => ({
  getDomainTree: vi.fn(),
  publish: vi.fn(),
  getMetrics: vi.fn(),
  getMessageStats: vi.fn(),
  getDomains: vi.fn(),
  createDomain: vi.fn(),
  updateDomain: vi.fn(),
  deleteDomain: vi.fn(),
  getClients: vi.fn(),
  createClient: vi.fn(),
  updateClient: vi.fn(),
  deleteClient: vi.fn(),
  getAclRules: vi.fn(),
  createAclRule: vi.fn(),
  updateAclRule: vi.fn(),
  deleteAclRule: vi.fn(),
  syncAcl: vi.fn(),
  getAuditLogs: vi.fn(),
  exportAuditLogsPdf: vi.fn(),
  getNetworkPresets: vi.fn(),
  simulateNetwork: vi.fn(),
}));

const authState = vi.hoisted(() => ({
  user: { username: 'admin', roleType: 'ADMIN', roleName: 'Admin', domainName: 'Root', clientId: 'client-a' } as any,
  login: vi.fn(),
  logout: vi.fn(),
}));

const publishState = vi.hoisted(() => ({
  selectedTopic: 'cross/domain',
  selectedNode: { key: 'domain', title: 'Domain', pathName: 'Root / Domain' } as any,
  qos: 1,
  retain: false,
  format: 'structured' as 'structured' | 'text',
  payload: '{"hello":"world"}',
  history: [] as any[],
  setSelectedTopic: vi.fn(),
  setSelectedNode: vi.fn(),
  setQos: vi.fn(),
  setRetain: vi.fn(),
  setFormat: vi.fn(),
  setPayload: vi.fn(),
  addHistory: vi.fn(),
}));

const subscribeState = vi.hoisted(() => ({
  topic: 'topic/a',
  qos: 1,
  activeTopic: 'topic/a' as string | null,
  messages: [{ topic: 'topic/a', payload: 'payload', timestamp: 1, meta: { converter: 'xml' } }] as any[],
  selectedKey: ['domain'],
  selectedName: 'Domain',
  mqttConnected: true,
  mqttProtocol: 'TLS',
  sseConnected: true,
  subscribedTopics: ['topic/a'],
  subscriptionCount: 1,
  setTopic: vi.fn(),
  setQos: vi.fn(),
  setSelectedKey: vi.fn(),
  setSelectedName: vi.fn(),
  connectMqtt: vi.fn(),
  disconnectMqtt: vi.fn(),
  subscribeTopic: vi.fn(),
  cancelTopic: vi.fn(),
  refreshSessionStatus: vi.fn(),
  clearMessages: vi.fn(),
}));

vi.mock('../services/api', () => ({ api: apiMock }));
vi.mock('../contexts/AuthContext', () => ({ useAuth: () => authState }));
vi.mock('../contexts/PublishContext', () => ({ usePublish: () => publishState }));
vi.mock('../contexts/SubscribeContext', () => ({ useSubscribe: () => subscribeState }));
vi.mock('echarts-for-react', () => ({ default: () => null }));

import AclManage from './AclManage';
import AuditLog from './AuditLog';
import ClientManage from './ClientManage';
import Dashboard from './Dashboard';
import DomainManage from './DomainManage';
import Login from './Login';
import NetworkSimulate from './NetworkSimulate';
import Publish from './Publish';
import Subscribe from './Subscribe';

const domainTree = [{
  key: 'root',
  title: 'Root',
  children: [{ key: 'cross/domain', title: 'Domain', domainCode: 'domain', topicPath: 'cross/domain', subscribeTopic: 'cross/domain/#', isLeaf: true }],
}];

const domains = [
  { id: 1, domainCode: 'root', domainName: 'Root', parentId: 2, status: 1 },
  { id: 2, domainCode: 'child', domainName: 'Child', parentId: 1, status: 0 },
];

function renderPage(ui: React.ReactElement) {
  return render(<MemoryRouter>{ui}</MemoryRouter>);
}

function clickModalOk(container: HTMLElement) {
  const ok = container.querySelector('.ant-modal button');
  expect(ok).toBeTruthy();
  fireEvent.click(ok!);
}

beforeEach(() => {
  apiMock.getDomainTree.mockResolvedValue({ success: true, data: domainTree });
  apiMock.publish.mockResolvedValue({ success: true });
  apiMock.getMetrics.mockResolvedValue({ success: true, data: { jvmUsedMemory: 128 } });
  apiMock.getMessageStats.mockResolvedValue({
    success: true,
    data: {
      onlineConnections: 2,
      totalMessagesReceived: 3,
      totalMessagesSent: 4,
      history: [{ time: Date.now(), messagesIn: 1, messagesOut: 2 }],
    },
  });
  apiMock.getDomains.mockResolvedValue({ success: true, data: domains });
  apiMock.getClients.mockResolvedValue({ success: true, data: [{ id: 1, username: 'alice', roleType: 'producer', domainId: 1 }] });
  apiMock.getAclRules.mockResolvedValue({ success: true, data: [{ id: 1, username: '*', topicFilter: 'cross/#', action: 'all', accessType: 'allow' }] });
  apiMock.syncAcl.mockResolvedValue({ success: true });
  apiMock.getAuditLogs.mockResolvedValue({
    success: true,
    data: {
      total: 2,
      records: [
        { id: 1, actionTime: '2026-05-29', clientId: 'client-a', actionType: 'json_schema_validate_fail', detail: 'bad schema', ipAddress: '::1' },
        { id: 2, actionTime: '2026-05-29', clientId: '', actionType: 'custom', detail: 'ok', ipAddress: '127.0.0.1' },
      ],
    },
  });
  apiMock.exportAuditLogsPdf.mockResolvedValue(new Blob(['pdf']));
  apiMock.getNetworkPresets.mockResolvedValue({
    success: true,
    data: [
      { name: 'fast', delay: 0, loss: 0, bandwidth: 0, description: 'fast network' },
      { name: 'slow', delay: 100, loss: 5, bandwidth: 10, description: 'slow network' },
    ],
  });
  apiMock.simulateNetwork.mockResolvedValue({ success: true, message: 'applied' });
  authState.user = { username: 'admin', roleType: 'ADMIN', roleName: 'Admin', domainName: 'Root', clientId: 'client-a' };
  publishState.selectedTopic = 'cross/domain';
  publishState.payload = '{"hello":"world"}';
  publishState.qos = 1;
  publishState.format = 'structured';
  publishState.history = [];
  subscribeState.mqttConnected = true;
  subscribeState.activeTopic = 'topic/a';
  subscribeState.messages = [{ topic: 'topic/a', payload: 'payload', timestamp: 1, meta: { converter: 'xml' } }];
});

describe('page smoke and key interactions', () => {
  it('logs in using the AuthContext result instead of sessionStorage', async () => {
    authState.login.mockResolvedValueOnce({ roleType: 'ADMIN' });
    const { container } = renderPage(<Login />);

    fireEvent.change(container.querySelector('input[autocomplete="username"]')!, { target: { value: 'admin' } });
    fireEvent.change(container.querySelector('input[autocomplete="current-password"]')!, { target: { value: 'admin123' } });
    fireEvent.click(container.querySelector('button[type="submit"]')!);

    await waitFor(() => expect(authState.login).toHaveBeenCalled());
  });

  it('renders dashboard metrics and topology data', async () => {
    renderPage(<Dashboard />);

    await waitFor(() => expect(apiMock.getMetrics).toHaveBeenCalled());
    expect(apiMock.getDomainTree).toHaveBeenCalled();
  });

  it('publishes messages and reports domain tree load failures', async () => {
    const { container, unmount } = renderPage(<Publish />);
    await waitFor(() => expect(apiMock.getDomainTree).toHaveBeenCalled());

    fireEvent.click(container.querySelector('button.ant-btn-primary')!);
    await waitFor(() => expect(apiMock.publish).toHaveBeenCalledWith('cross/domain', '{"hello":"world"}', 1, 'structured', false));
    expect(publishState.addHistory).toHaveBeenCalledWith(expect.objectContaining({ success: true }));
    unmount();

    apiMock.getDomainTree.mockRejectedValueOnce(new Error('tree failed'));
    renderPage(<Publish />);
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('tree failed'));
  });

  it('handles publish validation and failure branches', async () => {
    publishState.selectedTopic = '';
    const { container, rerender } = renderPage(<Publish />);
    fireEvent.click(container.querySelector('button.ant-btn-primary')!);
    expect(message.warning).toHaveBeenCalled();

    publishState.selectedTopic = 'cross/domain';
    publishState.payload = '   ';
    rerender(<MemoryRouter><Publish /></MemoryRouter>);
    fireEvent.click(container.querySelector('button.ant-btn-primary')!);
    expect(message.warning).toHaveBeenCalled();

    publishState.payload = 'body';
    publishState.qos = 0;
    apiMock.publish.mockResolvedValueOnce({ success: false, message: 'denied' });
    rerender(<MemoryRouter><Publish /></MemoryRouter>);
    fireEvent.click(container.querySelector('button.ant-btn-primary')!);
    await waitFor(() => expect(publishState.addHistory).toHaveBeenCalledWith(expect.objectContaining({ success: false })));
  });

  it('handles publish tree selection, option changes, history rendering and thrown errors', async () => {
    publishState.history = [
      { topic: 'cross/ok', qos: 1, format: 'structured', time: 'now', success: true },
      { topic: 'cross/bad', qos: 2, format: 'binary', time: 'then', success: false, error: 'bad payload' },
    ];
    apiMock.publish.mockRejectedValueOnce(new Error('publish crashed'));
    const { container } = renderPage(<Publish />);
    await waitFor(() => expect(apiMock.getDomainTree).toHaveBeenCalled());

    fireEvent.click(container.querySelector('[role="treeitem"]')!);
    expect(publishState.setSelectedNode).toHaveBeenCalled();
    expect(publishState.setSelectedTopic).toHaveBeenCalledWith('root');

    fireEvent.change(container.querySelector('textarea')!, { target: { value: 'plain text' } });
    expect(publishState.setPayload).toHaveBeenCalledWith('plain text');
    fireEvent.click(container.querySelector('button[value="2"]')!);
    expect(publishState.setQos).toHaveBeenCalledWith('2');
    fireEvent.click(container.querySelector('button[value="text"]')!);
    expect(publishState.setFormat).toHaveBeenCalledWith('text');
    fireEvent.click(container.querySelector('input[type="checkbox"]')!, { target: { checked: true } });
    expect(publishState.setRetain).toHaveBeenCalledWith(false);

    fireEvent.click(container.querySelector('button.ant-btn-primary')!);
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('publish crashed'));
    expect(publishState.addHistory).toHaveBeenCalledWith(expect.objectContaining({ success: false, error: 'publish crashed' }));
    expect(container.textContent).toContain('cross/bad');
  });

  it('renders subscribe controls and delegates each session action', async () => {
    const { container } = renderPage(<Subscribe />);
    await waitFor(() => expect(apiMock.getDomainTree).toHaveBeenCalled());

    fireEvent.click(screen.getByText('订阅主题'));
    await waitFor(() => expect(subscribeState.subscribeTopic).toHaveBeenCalled());
    fireEvent.click(screen.getByText('取消订阅'));
    await waitFor(() => expect(subscribeState.cancelTopic).toHaveBeenCalled());
    fireEvent.click(screen.getByText('断开 MQTT'));
    await waitFor(() => expect(subscribeState.disconnectMqtt).toHaveBeenCalled());
    fireEvent.click(screen.getByText('清空'));

    expect(subscribeState.clearMessages).toHaveBeenCalled();
  });

  it('shows subscribe loading errors and connect failures', async () => {
    subscribeState.mqttConnected = false;
    subscribeState.activeTopic = null;
    subscribeState.messages = [];
    subscribeState.connectMqtt.mockRejectedValueOnce(new Error('connect failed'));
    apiMock.getDomainTree.mockRejectedValueOnce(new Error('tree failed'));
    const { container } = renderPage(<Subscribe />);

    await waitFor(() => expect(message.error).toHaveBeenCalledWith('tree failed'));
    fireEvent.click(container.querySelector('button.ant-btn-primary')!);
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('connect failed'));
  });

  it('handles subscribe tree selection, remembered-topic close and action failures', async () => {
    subscribeState.mqttConnected = true;
    subscribeState.activeTopic = 'topic/a';
    subscribeState.messages = [];
    subscribeState.subscribedTopics = ['topic/a'];
    subscribeState.subscribeTopic.mockRejectedValueOnce(new Error('subscribe failed'));
    subscribeState.cancelTopic.mockRejectedValueOnce(new Error('cancel failed'));
    subscribeState.disconnectMqtt.mockRejectedValueOnce(new Error('disconnect failed'));
    const { container } = renderPage(<Subscribe />);
    await waitFor(() => expect(apiMock.getDomainTree).toHaveBeenCalled());

    fireEvent.click(container.querySelector('[role="treeitem"]')!);
    expect(subscribeState.setSelectedKey).toHaveBeenCalledWith(['root']);
    expect(subscribeState.setSelectedName).toHaveBeenCalled();
    expect(subscribeState.setTopic).toHaveBeenCalledWith('root/#');

    fireEvent.change(container.querySelector('input')!, { target: { value: 'manual/#' } });
    expect(subscribeState.setTopic).toHaveBeenCalledWith('manual/#');
    fireEvent.click(container.querySelector('button[value="2"]')!);
    expect(subscribeState.setQos).toHaveBeenCalledWith('2');

    fireEvent.click(container.querySelector('button[aria-label="close"]')!);
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('cancel failed'));

    fireEvent.click(screen.getByText('订阅主题'));
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('subscribe failed'));

    subscribeState.cancelTopic.mockRejectedValueOnce(new Error('cancel active failed'));
    fireEvent.click(screen.getByText('取消订阅'));
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('cancel active failed'));

    fireEvent.click(screen.getByText('断开 MQTT'));
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('disconnect failed'));
  });

  it('renders domain, client and ACL management tables and primary actions', async () => {
    const domain = renderPage(<DomainManage />);
    await waitFor(() => expect(apiMock.getDomains).toHaveBeenCalled());
    fireEvent.click(domain.container.querySelector('button.ant-btn-primary')!);
    expect(domain.container.querySelector('.ant-modal')).toBeTruthy();
    domain.unmount();

    const client = renderPage(<ClientManage />);
    await waitFor(() => expect(apiMock.getClients).toHaveBeenCalled());
    fireEvent.click(client.container.querySelector('button.ant-btn-primary')!);
    expect(client.container.querySelector('.ant-modal')).toBeTruthy();
    client.unmount();

    const acl = renderPage(<AclManage />);
    await waitFor(() => expect(apiMock.getAclRules).toHaveBeenCalled());
    fireEvent.click(acl.container.querySelector('button')!);
    await waitFor(() => expect(apiMock.syncAcl).toHaveBeenCalled());
  });

  it('creates, edits and deletes domains', async () => {
    const created = renderPage(<DomainManage />);
    await waitFor(() => expect(apiMock.getDomains).toHaveBeenCalled());
    fireEvent.click(created.container.querySelector('button.ant-btn-primary')!);
    clickModalOk(created.container);
    await waitFor(() => expect(apiMock.createDomain).toHaveBeenCalledWith(expect.objectContaining({ domainCode: 'domain' })));
    created.unmount();

    const edited = renderPage(<DomainManage />);
    await waitFor(() => expect(apiMock.getDomains).toHaveBeenCalled());
    const editButtons = edited.container.querySelectorAll('button');
    fireEvent.click(editButtons[1]);
    clickModalOk(edited.container);
    await waitFor(() => expect(apiMock.updateDomain).toHaveBeenCalledWith(1, expect.objectContaining({ domainName: 'Domain' })));
    edited.unmount();

    const deleted = renderPage(<DomainManage />);
    await waitFor(() => expect(apiMock.getDomains).toHaveBeenCalled());
    const deleteButtons = deleted.container.querySelectorAll('button');
    fireEvent.click(deleteButtons[2]);
    await waitFor(() => expect(apiMock.deleteDomain).toHaveBeenCalledWith(1));
  });

  it('reports domain save and delete failures', async () => {
    apiMock.createDomain.mockRejectedValueOnce(new Error('create domain failed'));
    const saveFailed = renderPage(<DomainManage />);
    await waitFor(() => expect(apiMock.getDomains).toHaveBeenCalled());
    fireEvent.click(saveFailed.container.querySelector('button.ant-btn-primary')!);
    clickModalOk(saveFailed.container);
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('create domain failed'));
    saveFailed.unmount();

    apiMock.deleteDomain.mockRejectedValueOnce(new Error('delete domain failed'));
    const deleteFailed = renderPage(<DomainManage />);
    await waitFor(() => expect(apiMock.getDomains).toHaveBeenCalled());
    fireEvent.click(deleteFailed.container.querySelectorAll('button')[2]);
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('delete domain failed'));
  });

  it('creates, edits and deletes clients', async () => {
    const created = renderPage(<ClientManage />);
    await waitFor(() => expect(apiMock.getClients).toHaveBeenCalled());
    fireEvent.click(created.container.querySelector('button.ant-btn-primary')!);
    clickModalOk(created.container);
    await waitFor(() => expect(apiMock.createClient).toHaveBeenCalledWith(expect.objectContaining({
      username: 'alice',
      domainId: null,
      passwordHash: 'secret',
    })));
    created.unmount();

    const edited = renderPage(<ClientManage />);
    await waitFor(() => expect(apiMock.getClients).toHaveBeenCalled());
    fireEvent.click(edited.container.querySelectorAll('button')[1]);
    clickModalOk(edited.container);
    await waitFor(() => expect(apiMock.updateClient).toHaveBeenCalledWith(1, expect.objectContaining({ username: 'alice' })));
    edited.unmount();

    const deleted = renderPage(<ClientManage />);
    await waitFor(() => expect(apiMock.getClients).toHaveBeenCalled());
    fireEvent.click(deleted.container.querySelectorAll('button')[2]);
    await waitFor(() => expect(apiMock.deleteClient).toHaveBeenCalledWith(1));
  });

  it('reports client save and delete failures', async () => {
    apiMock.updateClient.mockRejectedValueOnce(new Error('update client failed'));
    const saveFailed = renderPage(<ClientManage />);
    await waitFor(() => expect(apiMock.getClients).toHaveBeenCalled());
    fireEvent.click(saveFailed.container.querySelectorAll('button')[1]);
    clickModalOk(saveFailed.container);
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('update client failed'));
    saveFailed.unmount();

    apiMock.deleteClient.mockRejectedValueOnce(new Error('delete client failed'));
    const deleteFailed = renderPage(<ClientManage />);
    await waitFor(() => expect(apiMock.getClients).toHaveBeenCalled());
    fireEvent.click(deleteFailed.container.querySelectorAll('button')[2]);
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('delete client failed'));
  });

  it('creates, edits, deletes and handles ACL sync failures', async () => {
    const created = renderPage(<AclManage />);
    await waitFor(() => expect(apiMock.getAclRules).toHaveBeenCalled());
    fireEvent.click(created.container.querySelector('button.ant-btn-primary')!);
    clickModalOk(created.container);
    await waitFor(() => expect(apiMock.createAclRule).toHaveBeenCalledWith(expect.objectContaining({ topicFilter: 'cross/#' })));
    created.unmount();

    const edited = renderPage(<AclManage />);
    await waitFor(() => expect(apiMock.getAclRules).toHaveBeenCalled());
    fireEvent.click(edited.container.querySelectorAll('button')[2]);
    clickModalOk(edited.container);
    await waitFor(() => expect(apiMock.updateAclRule).toHaveBeenCalledWith(1, expect.objectContaining({ action: 'all' })));
    edited.unmount();

    const deleted = renderPage(<AclManage />);
    await waitFor(() => expect(apiMock.getAclRules).toHaveBeenCalled());
    fireEvent.click(deleted.container.querySelectorAll('button')[3]);
    await waitFor(() => expect(apiMock.deleteAclRule).toHaveBeenCalledWith(1));
    deleted.unmount();

    apiMock.syncAcl.mockResolvedValueOnce({ success: false, message: 'sync rejected' });
    const syncRejected = renderPage(<AclManage />);
    await waitFor(() => expect(apiMock.getAclRules).toHaveBeenCalled());
    fireEvent.click(syncRejected.container.querySelectorAll('button')[0]);
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('sync rejected'));
  });

  it('reports ACL save, delete and sync exceptions', async () => {
    apiMock.createAclRule.mockRejectedValueOnce(new Error('create acl failed'));
    const saveFailed = renderPage(<AclManage />);
    await waitFor(() => expect(apiMock.getAclRules).toHaveBeenCalled());
    fireEvent.click(saveFailed.container.querySelector('button.ant-btn-primary')!);
    clickModalOk(saveFailed.container);
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('create acl failed'));
    saveFailed.unmount();

    apiMock.deleteAclRule.mockRejectedValueOnce(new Error('delete acl failed'));
    const deleteFailed = renderPage(<AclManage />);
    await waitFor(() => expect(apiMock.getAclRules).toHaveBeenCalled());
    fireEvent.click(deleteFailed.container.querySelectorAll('button')[3]);
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('delete acl failed'));
    deleteFailed.unmount();

    apiMock.syncAcl.mockRejectedValueOnce(new Error('sync crashed'));
    const syncFailed = renderPage(<AclManage />);
    await waitFor(() => expect(apiMock.getAclRules).toHaveBeenCalled());
    fireEvent.click(syncFailed.container.querySelectorAll('button')[0]);
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('sync crashed'));
  });

  it('renders audit logs, reloads once and exports a PDF', async () => {
    const { container } = renderPage(<AuditLog />);

    await waitFor(() => expect(apiMock.getAuditLogs).toHaveBeenCalledTimes(1));
    fireEvent.change(container.querySelector('input')!, { target: { value: 'client-a' } });
    fireEvent.keyDown(container.querySelector('input')!, { key: 'Enter' });
    fireEvent.change(container.querySelector('select')!, { target: { value: 'acl_deny' } });
    fireEvent.click(screen.getByText('next-page'));
    await waitFor(() => expect(apiMock.getAuditLogs).toHaveBeenCalledWith(2, 20, expect.any(String), expect.any(String)));
    fireEvent.click(container.querySelector('button.ant-btn-primary')!);
    await waitFor(() => expect(apiMock.exportAuditLogsPdf).toHaveBeenCalled());
    expect(URL.createObjectURL).toHaveBeenCalled();
  });

  it('reports audit log load and export failures', async () => {
    apiMock.getAuditLogs.mockRejectedValueOnce(new Error('audit load failed'));
    const loadFailed = renderPage(<AuditLog />);
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('audit load failed'));
    loadFailed.unmount();

    apiMock.exportAuditLogsPdf.mockRejectedValueOnce(new Error('export failed'));
    const exportFailed = renderPage(<AuditLog />);
    await waitFor(() => expect(apiMock.getAuditLogs).toHaveBeenCalled());
    fireEvent.click(exportFailed.container.querySelector('button.ant-btn-primary')!);
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('export failed'));
  });

  it('loads network presets, applies a selected preset and blocks non-admins', async () => {
    const network = renderPage(<NetworkSimulate />);
    await waitFor(() => expect(apiMock.getNetworkPresets).toHaveBeenCalled());

    fireEvent.click(network.container.querySelector('.network-card')!);
    fireEvent.click(network.container.querySelector('button.ant-btn-primary')!);
    await waitFor(() => expect(apiMock.simulateNetwork).toHaveBeenCalled());
    network.unmount();

    authState.user = { ...authState.user, roleType: 'consumer' };
    renderPage(<NetworkSimulate />);
    await waitFor(() => expect(screen.getAllByText((_, node) => Boolean(node?.textContent?.includes('无访问权限'))).length).toBeGreaterThan(0));
  });

  it('reports network preset and apply failures', async () => {
    apiMock.getNetworkPresets.mockRejectedValueOnce(new Error('preset failed'));
    const failedLoad = renderPage(<NetworkSimulate />);
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('preset failed'));
    failedLoad.unmount();

    apiMock.simulateNetwork.mockRejectedValueOnce(new Error('tc failed'));
    const failedApply = renderPage(<NetworkSimulate />);
    await waitFor(() => expect(apiMock.getNetworkPresets).toHaveBeenCalled());
    fireEvent.click(failedApply.container.querySelector('.network-card')!);
    fireEvent.click(failedApply.container.querySelector('button.ant-btn-primary')!);
    await waitFor(() => expect(message.error).toHaveBeenLastCalledWith('设置失败: tc failed'));
  });
});
