/**
 * 连接状态浮动指示器
 *
 * 功能说明：
 * - 固定悬浮在页面底部，实时显示后端/EMQX/MQTT 三路连接状态
 * - 每 10 秒轮询后端与 EMQX 健康检查接口
 * - 使用 AbortController 在每次轮询前取消上一次未完成的请求，避免竞态
 * - 未登录时不渲染
 */
import React, { useEffect, useState } from 'react';
import { Badge, Space, Tag, Tooltip } from 'antd';
import { api } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import { useSubscribe } from '../contexts/SubscribeContext';

/** 健康检查请求超时时间（毫秒），超时后 AbortController 取消请求 */
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
      // 取消上一次未完成的请求，防止并发请求导致状态错乱
      if (activeTimeoutId) clearTimeout(activeTimeoutId);
      if (activeController) activeController.abort();

      activeController = new AbortController();
      const controller = activeController;
      // 设置超时自动取消，避免请求无限挂起
      activeTimeoutId = setTimeout(() => controller?.abort(), STATUS_CHECK_TIMEOUT);

      try {
        const [backendResp, emqxResp] = await Promise.all([
          api.checkStatus(controller.signal),
          api.checkEmqxStatus(controller.signal),
        ]);

        // 请求成功后清理超时计时器和控制器引用
        if (activeTimeoutId) clearTimeout(activeTimeoutId);
        activeTimeoutId = null;
        activeController = null;
        setBackendConnected(backendResp.status === 'ok');
        setEmqxConnected(emqxResp.status === 'online');
      } catch {
        // 请求失败（含超时/取消）同样清理引用，并标记为离线
        if (activeTimeoutId) clearTimeout(activeTimeoutId);
        activeTimeoutId = null;
        activeController = null;
        setBackendConnected(false);
        setEmqxConnected(false);
      }
    };

    void checkConnection();
    // 每 10 秒轮询一次连接状态
    const interval = setInterval(() => {
      void checkConnection();
    }, 10000);

    // 组件卸载时清理轮询定时器和可能正在进行的请求
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
    <div className="floating-status">
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
