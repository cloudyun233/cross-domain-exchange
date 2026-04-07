import React, { useEffect, useState } from 'react';
import { Badge, Space, Tag, Tooltip } from 'antd';
import { api } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import { useSubscribe } from '../contexts/SubscribeContext';

const STATUS_CHECK_TIMEOUT = 3000;

const ConnectionStatus: React.FC = () => {
  const [backendConnected, setBackendConnected] = useState(false);
  const [emqxConnected, setEmqxConnected] = useState(false);
  const { user } = useAuth();
  const { mqttConnected, mqttProtocol } = useSubscribe();

  useEffect(() => {
    if (!user) {
      setBackendConnected(false);
      setEmqxConnected(false);
      return;
    }

    let activeTimeoutId: NodeJS.Timeout | null = null;
    let activeController: AbortController | null = null;

    const checkConnection = async () => {
      if (activeTimeoutId) clearTimeout(activeTimeoutId);
      if (activeController) activeController.abort();

      activeController = new AbortController();
      const controller = activeController;
      activeTimeoutId = setTimeout(() => controller?.abort(), STATUS_CHECK_TIMEOUT);

      try {
        const [backendResp, emqxResp] = await Promise.all([
          api.checkStatus(controller.signal),
          api.checkEmqxStatus(controller.signal),
        ]);

        if (activeTimeoutId) clearTimeout(activeTimeoutId);
        activeTimeoutId = null;
        activeController = null;
        setBackendConnected(backendResp.status === 'ok');
        setEmqxConnected(emqxResp.status === 'online');
      } catch {
        if (activeTimeoutId) clearTimeout(activeTimeoutId);
        activeTimeoutId = null;
        activeController = null;
        setBackendConnected(false);
        setEmqxConnected(false);
      }
    };

    void checkConnection();
    const interval = setInterval(() => {
      void checkConnection();
    }, 10000);

    return () => {
      clearInterval(interval);
      if (activeTimeoutId) clearTimeout(activeTimeoutId);
      if (activeController) activeController.abort();
    };
  }, [user]);

  if (!user) {
    return null;
  }

  const protocolColor = mqttProtocol === 'TLS' ? 'green' : mqttProtocol === 'TCP' ? 'orange' : 'default';

  return (
    <div
      style={{
        position: 'fixed',
        bottom: 16,
        right: 16,
        zIndex: 9999,
        background: 'rgba(255,255,255,0.96)',
        padding: '10px 14px',
        borderRadius: 10,
        boxShadow: '0 10px 30px rgba(15, 39, 66, 0.12)',
        border: '1px solid rgba(24, 144, 255, 0.12)',
      }}
    >
      <Space size="middle" wrap>
        <Badge status={backendConnected ? 'success' : 'error'} text={backendConnected ? '后端在线' : '后端离线'} />
        <Badge status={emqxConnected ? 'success' : 'error'} text={emqxConnected ? 'EMQX 在线' : 'EMQX 离线'} />
        <Badge status={mqttConnected ? 'success' : 'default'} text={mqttConnected ? 'MQTT 已连接' : 'MQTT 已断开'} />
        <Tag color={protocolColor}>协议: {mqttProtocol || '未连接'}</Tag>
        {user.clientId && (
          <Tooltip title="MQTT Client ID">
            <span
              style={{
                fontSize: 12,
                color: '#666',
                background: '#f5f7fa',
                padding: '2px 8px',
                borderRadius: 999,
              }}
            >
              ID: {user.clientId}
            </span>
          </Tooltip>
        )}
      </Space>
    </div>
  );
};

export default ConnectionStatus;
