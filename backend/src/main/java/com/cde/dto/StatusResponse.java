package com.cde.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 健康检查响应。
 * <p>
 * status取值：
 * <ul>
 *   <li>"ok" — 后端服务正常</li>
 *   <li>"online" — EMQX消息代理在线</li>
 *   <li>"offline" — EMQX消息代理离线</li>
 * </ul>
 */
@Data
@AllArgsConstructor
public class StatusResponse {
    /** 服务状态标识 */
    private String status;
}
