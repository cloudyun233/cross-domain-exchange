import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { message } from 'antd';

const authState = vi.hoisted(() => ({ isAuthenticated: true }));
const apiMock = vi.hoisted(() => ({
  openSseChannel: vi.fn(),
  getSubscribeSessionStatus: vi.fn(),
  connectSubscribeSession: vi.fn(),
  disconnectSubscribeSession: vi.fn(),
  subscribeToTopic: vi.fn(),
  cancelSubscribe: vi.fn(),
}));

vi.mock('./AuthContext', () => ({
  useAuth: () => authState,
}));

vi.mock('../services/api', () => ({
  api: apiMock,
}));

import { SubscribeProvider, useSubscribe } from './SubscribeContext';

class FakeEventSource {
  listeners: Record<string, Array<(event: any) => void>> = {};
  close = vi.fn();
  onerror?: () => void;

  addEventListener(type: string, callback: (event: any) => void) {
    this.listeners[type] = [...(this.listeners[type] || []), callback];
  }

  emit(type: string, data?: unknown) {
    (this.listeners[type] || []).forEach((callback) => callback({ data }));
  }

  fail() {
    this.onerror?.();
  }
}

const sources: FakeEventSource[] = [];

const status = (overrides: Record<string, unknown> = {}) => ({
  mqttConnected: false,
  protocol: 'offline',
  sseConnected: false,
  subscribedTopics: [],
  subscriptionCount: 0,
  ...overrides,
});

const Probe = () => {
  const ctx = useSubscribe();
  return (
    <div>
      <div data-testid="sse">{String(ctx.sseConnected)}</div>
      <div data-testid="mqtt">{String(ctx.mqttConnected)}</div>
      <div data-testid="topic">{ctx.topic}</div>
      <div data-testid="active">{ctx.activeTopic || ''}</div>
      <div data-testid="messages">{ctx.messages.length}</div>
      <div data-testid="payload">{ctx.messages[0]?.payload || ''}</div>
      <div data-testid="meta">{ctx.messages[0]?.meta?.converter || ''}</div>
      <button onClick={() => ctx.setTopic('topic/a')}>set-topic</button>
      <button onClick={() => void ctx.connectMqtt()}>connect</button>
      <button onClick={() => void ctx.subscribeTopic()}>subscribe</button>
      <button onClick={() => void ctx.cancelTopic('topic/a').catch(() => undefined)}>cancel</button>
      <button onClick={() => void ctx.disconnectMqtt()}>disconnect</button>
      <button onClick={ctx.clearMessages}>clear</button>
    </div>
  );
};

function renderSubscribe() {
  sources.length = 0;
  apiMock.openSseChannel.mockImplementation(() => {
    const source = new FakeEventSource();
    sources.push(source);
    return source;
  });
  apiMock.getSubscribeSessionStatus.mockResolvedValue({ success: true, data: status({ sseConnected: true }) });
  return render(<SubscribeProvider><Probe /></SubscribeProvider>);
}

describe('SubscribeContext', () => {
  it('opens SSE, applies session status and buffers parsed messages', async () => {
    renderSubscribe();

    act(() => sources[0].emit('connected', 'ok'));
    await waitFor(() => expect(screen.getByTestId('sse')).toHaveTextContent('true'));

    act(() => sources[0].emit('message', JSON.stringify({
      topic: 'topic/a',
      payload: JSON.stringify({ _meta: { converter: 'xml' }, data: { a: 1 } }),
      timestamp: 1,
    })));

    expect(screen.getByTestId('messages')).toHaveTextContent('1');
    expect(screen.getByTestId('payload')).toHaveTextContent('"a": 1');
    expect(screen.getByTestId('meta')).toHaveTextContent('xml');

    act(() => sources[0].emit('message', '{broken'));
    act(() => sources[0].emit('error', JSON.stringify({ message: 'backend error' })));
    expect(message.error).toHaveBeenCalledWith('backend error');

    fireEvent.click(screen.getByText('clear'));
    expect(screen.getByTestId('messages')).toHaveTextContent('0');
  });

  it('connects, subscribes, cancels and disconnects through the backend session API', async () => {
    apiMock.connectSubscribeSession.mockResolvedValue({ success: true, message: 'connected', data: status({
      mqttConnected: true,
      protocol: 'TLS',
      sseConnected: true,
      subscribedTopics: ['topic/a'],
      subscriptionCount: 1,
    }) });
    apiMock.subscribeToTopic.mockResolvedValue({ success: true, data: status({
      mqttConnected: true,
      protocol: 'TLS',
      sseConnected: true,
      subscribedTopics: ['topic/a'],
      subscriptionCount: 1,
    }) });
    apiMock.cancelSubscribe.mockResolvedValue({ success: true, data: status({ mqttConnected: true, sseConnected: true }) });
    apiMock.disconnectSubscribeSession.mockResolvedValue({ success: true, message: 'disconnected', data: status() });
    renderSubscribe();

    act(() => sources[0].emit('connected', 'ok'));
    await waitFor(() => expect(screen.getByTestId('sse')).toHaveTextContent('true'));

    fireEvent.click(screen.getByText('set-topic'));
    fireEvent.click(screen.getByText('connect'));
    act(() => sources[sources.length - 1].emit('connected', 'ok'));
    await waitFor(() => expect(screen.getByTestId('mqtt')).toHaveTextContent('true'));

    fireEvent.click(screen.getByText('subscribe'));
    await waitFor(() => expect(screen.getByTestId('active')).toHaveTextContent('topic/a'));

    fireEvent.click(screen.getByText('cancel'));
    await waitFor(() => expect(apiMock.cancelSubscribe).toHaveBeenCalledWith('topic/a'));

    fireEvent.click(screen.getByText('disconnect'));
    await waitFor(() => expect(screen.getByTestId('mqtt')).toHaveTextContent('false'));
  });

  it('surfaces cancel failures and clears unauthenticated session state', async () => {
    apiMock.cancelSubscribe.mockRejectedValueOnce(new Error('broker failed'));
    renderSubscribe();

    fireEvent.click(screen.getByText('cancel'));
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('broker failed'));

    authState.isAuthenticated = false;
    render(<SubscribeProvider><Probe /></SubscribeProvider>);
    expect(screen.getAllByTestId('mqtt').at(-1)).toHaveTextContent('false');
    authState.isAuthenticated = true;
  });

  it('lets manual connect re-enable SSE after reconnect exhaustion', async () => {
    vi.useFakeTimers();
    apiMock.connectSubscribeSession.mockResolvedValue({ success: true, data: status({ mqttConnected: true, sseConnected: true }) });
    renderSubscribe();

    for (let i = 0; i < 6; i += 1) {
      act(() => sources[sources.length - 1].fail());
      await act(async () => {
        await vi.runOnlyPendingTimersAsync();
      });
    }

    expect(message.error).toHaveBeenCalledWith(expect.stringContaining('SSE'));

    await act(async () => {
      fireEvent.click(screen.getByText('connect'));
      sources[sources.length - 1].emit('connected', 'ok');
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(apiMock.connectSubscribeSession).toHaveBeenCalled();
  });

});
