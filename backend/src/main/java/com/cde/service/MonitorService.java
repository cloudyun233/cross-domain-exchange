package com.cde.service;

import java.util.Map;

/**
 * 监控服务接口 (论文4.5.3类图: MonitorService)
 */
public interface MonitorService {
    /** 获取综合监控面板数据 */
    Map<String, Object> getTrafficStats();
    /** 获取在线客户端统计 */
    Map<String, Object> getClientStats();
    /** 获取主题统计 */
    Map<String, Object> getTopicStats();
    /** 获取系统指标 */
    Map<String, Object> getSystemMetrics();
}
