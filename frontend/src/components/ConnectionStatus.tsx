import React, { useState, useEffect } from 'react';
import { Badge } from 'antd';
import { api } from '../services/api';

const STATUS_CHECK_TIMEOUT = 3000;

const ConnectionStatus: React.FC = () => {
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const checkConnection = async () => {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), STATUS_CHECK_TIMEOUT);

      try {
        const response = await api.checkStatus(controller.signal);
        clearTimeout(timeoutId);
        setConnected(response.status === 'ok');
      } catch {
        setConnected(false);
      }
    };

    checkConnection();
    const interval = setInterval(checkConnection, 10000);
    return () => clearInterval(interval);
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
    }}>
      <Badge 
        status={connected ? 'success' : 'error'} 
        text={connected ? '后端在线' : '后端离线'}
      />
    </div>
  );
};

export default ConnectionStatus;
