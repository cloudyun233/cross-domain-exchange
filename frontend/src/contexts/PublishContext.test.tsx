import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PublishProvider, usePublish } from './PublishContext';

const Probe = () => {
  const ctx = usePublish();
  return (
    <div>
      <div data-testid="topic">{ctx.selectedTopic}</div>
      <div data-testid="format">{ctx.format}</div>
      <div data-testid="payload">{ctx.payload}</div>
      <div data-testid="history">{ctx.history.length}</div>
      <button onClick={() => ctx.setSelectedTopic('cross/domain')}>topic</button>
      <button onClick={() => ctx.setSelectedNode({ key: 'k', title: 'Domain', topicPath: 'cross/domain' })}>node</button>
      <button onClick={() => ctx.setQos(2)}>qos</button>
      <button onClick={() => ctx.setRetain(true)}>retain</button>
      <button onClick={() => ctx.setFormat('text')}>text</button>
      <button onClick={() => ctx.setFormat('structured')}>structured</button>
      <button onClick={() => ctx.setPayload('custom payload')}>payload</button>
      <button onClick={() => {
        for (let i = 0; i < 21; i += 1) {
          ctx.addHistory({ topic: `t/${i}`, qos: 1, format: 'text', time: String(i), success: i % 2 === 0 });
        }
      }}>
        history
      </button>
      <button onClick={ctx.clearHistory}>clear</button>
    </div>
  );
};

describe('PublishContext', () => {
  it('loads broken storage safely and persists form changes', () => {
    sessionStorage.setItem('publish_state', '{broken');
    render(<PublishProvider><Probe /></PublishProvider>);

    expect(screen.getByTestId('format')).toHaveTextContent('structured');

    fireEvent.click(screen.getByText('topic'));
    fireEvent.click(screen.getByText('node'));
    fireEvent.click(screen.getByText('qos'));
    fireEvent.click(screen.getByText('retain'));
    fireEvent.click(screen.getByText('payload'));

    const saved = JSON.parse(sessionStorage.getItem('publish_state') || '{}');
    expect(saved).toMatchObject({
      selectedTopic: 'cross/domain',
      qos: 2,
      retain: true,
      payload: 'custom payload',
    });
  });

  it('switches format samples and keeps only the newest twenty history entries', () => {
    render(<PublishProvider><Probe /></PublishProvider>);

    fireEvent.click(screen.getByText('text'));
    expect(screen.getByTestId('format')).toHaveTextContent('text');
    expect(screen.getByTestId('payload')).not.toHaveTextContent('{');

    fireEvent.click(screen.getByText('structured'));
    expect(screen.getByTestId('payload')).toHaveTextContent('patientId');

    fireEvent.click(screen.getByText('history'));
    expect(screen.getByTestId('history')).toHaveTextContent('20');
    expect(JSON.parse(sessionStorage.getItem('publish_state') || '{}').history).toHaveLength(20);

    fireEvent.click(screen.getByText('clear'));
    expect(screen.getByTestId('history')).toHaveTextContent('0');
  });

});
