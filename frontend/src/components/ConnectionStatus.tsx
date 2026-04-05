import React, { useState, useEffect } from 'react';
import { Badge, Tooltip } from 'antd';
import { api } from '../services/api';
import { useAuth } from '../contexts/AuthContext';

const STATUS_CHECK_TIMEOUT = 3000;

const ConnectionStatus: React.FC = () => {
  const [connected, setConnected] = useState(false);
  const { user } = useAuth();

  useEffect(() => {
    let activeTimeoutId: NodeJS.Timeout | null = null;
    let activeController: AbortController | null = null;

    const checkConnection = async () => {
      if (activeTimeoutId) clearTimeout(activeTimeoutId);
      if (activeController) activeController.abort();

      activeController = new AbortController();
      const controller = activeController;
      activeTimeoutId = setTimeout(() => controller?.abort(), STATUS_CHECK_TIMEOUT);

      try {
        const response = await api.checkStatus(controller.signal);
        if (activeTimeoutId) clearTimeout(activeTimeoutId);
        activeTimeoutId = null;
        activeController = null;
        setConnected(response.status === 'ok');
      } catch {
        if (activeTimeoutId) clearTimeout(activeTimeoutId);
        activeTimeoutId = null;
        activeController = null;
        setConnected(false);
      }
    };

    checkConnection();
    const interval = setInterval(checkConnection, 10000);
    
    return () => {
      clearInterval(interval);
      if (activeTimeoutId) clearTimeout(activeTimeoutId);
      if (activeController) activeController.abort();
    };
  }, []);

  return (
    <div style={{
      position: 'fixed',
      top: 16,
      right: 16,
      zIndex: 9999,
      background: '#fff',
      padding: '8px 16px',
      borderRadius: 6,
      boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
      display: 'flex',
      alignItems: 'center',
      gap: '12px',
    }}>
      <Badge 
        status={connected ? 'success' : 'error'} 
        text={connected ? '后端在线' : '后端离线'}
      />
      {user && user.clientId && (
        <Tooltip title="MQTT Client ID">
          <span style={{ 
            fontSize: '12px', 
            color: '#666',
            background: '#f0f0f0',
            padding: '2px 8px',
            borderRadius: 4
          }}>
            ID: {user.clientId}
          </span>
        </Tooltip>
      )}
    </div>
  );
};

export default ConnectionStatus;
