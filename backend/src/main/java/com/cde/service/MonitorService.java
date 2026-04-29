package com.cde.service;

import java.util.Map;

/**
 * 监控服务接口 (论文4.5.3类图: MonitorService)
 */
public interface MonitorService {
    /** 获取综合监控面板数据（数据来源：EMQX Stats API + Metrics API + 流量历史滑动窗口） */
    Map<String, Object> getTrafficStats();
    /** 获取在线客户端统计（数据来源：EMQX Clients API） */
    Map<String, Object> getClientStats();
    /** 获取主题统计（数据来源：EMQX Subscriptions API，实时查询） */
    Map<String, Object> getTopicStats();
    /** 获取系统指标（数据来源：JVM Runtime，非EMQX） */
    Map<String, Object> getSystemMetrics();
}
