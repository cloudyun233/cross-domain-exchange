package com.cde.controller;

import com.cde.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/network")
@PreAuthorize("hasRole('ADMIN')")
public class NetworkController {

    /**
     * 自动检测网络接口（优先使用 eth0，其次是默认路由接口）
     */
    private String detectNetworkInterface() {
        try {
            // 先尝试 eth0
            ProcessBuilder checkEth0 = new ProcessBuilder("sh", "-c", "ip link show eth0 2>/dev/null");
            Process p = checkEth0.start();
            if (p.waitFor() == 0) {
                return "eth0";
            }
            
            // 尝试获取默认路由接口
            ProcessBuilder getDefaultRoute = new ProcessBuilder("sh", "-c", 
                "ip route show default | awk '/default/ {print $5}'");
            p = getDefaultRoute.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String iface = reader.readLine();
            if (iface != null && !iface.trim().isEmpty()) {
                return iface.trim();
            }
        } catch (Exception e) {
            log.warn("自动检测网络接口失败，使用默认 eth0", e);
        }
        return "eth0";
    }

    @PostMapping("/simulate")
    public ApiResponse<String> simulate(
            @RequestParam(defaultValue = "0") int delayMs,
            @RequestParam(defaultValue = "0") int lossPercent,
            @RequestParam(defaultValue = "0") int bandwidthMbps) {
        try {
            String iface = detectNetworkInterface();
            log.info("检测到网络接口: {}", iface);
            
            String cmd;
            if (delayMs == 0 && lossPercent == 0 && bandwidthMbps == 0) {
                cmd = String.format("tc qdisc del dev %s root 2>/dev/null; echo 'Network reset'", iface);
                log.info("弱网模拟已重置: 无限制模式");
            } else {
                cmd = String.format(
                    "tc qdisc replace dev %s root netem delay %dms loss %d%%%s",
                    iface, delayMs, lossPercent,
                    bandwidthMbps > 0 ? String.format(" rate %dmbit", bandwidthMbps) : "");
                log.info("弱网模拟命令: {}", cmd);
            }

            ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // 读取输出以便调试
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("tc 输出: {}", line);
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("tc 命令执行失败，退出码: " + exitCode);
            }

            String scenario = getScenarioName(delayMs, lossPercent, bandwidthMbps);
            return ApiResponse.ok(String.format("弱网模拟已设置: %s (接口: %s)", scenario, iface), null);
        } catch (Exception e) {
            log.error("弱网模拟设置失败", e);
            return ApiResponse.fail("弱网模拟设置失败(需要在Linux Docker环境中运行): " + e.getMessage());
        }
    }

    private String getScenarioName(int delayMs, int lossPercent, int bandwidthMbps) {
        if (delayMs == 0 && lossPercent == 0 && bandwidthMbps == 0) return "无限制";
        if (delayMs == 10 && lossPercent == 0) return "标准网络";
        if (delayMs == 100 && lossPercent == 5 && bandwidthMbps == 10) return "政务跨域波动";
        if (delayMs == 250 && lossPercent == 15 && bandwidthMbps == 2) return "普通弱网";
        if (delayMs == 500 && lossPercent == 30 && bandwidthMbps == 1) return "极端弱网";
        return String.format("自定义(延迟=%dms, 丢包=%d%%, 带宽=%dMbps)", delayMs, lossPercent, bandwidthMbps);
    }

    @GetMapping("/presets")
    public ApiResponse<Object> getPresets() {
        return ApiResponse.ok(java.util.List.of(
            Map.of("name", "无限制", "delay", 0, "loss", 0, "bandwidth", 0, "description", "默认状态，不调用tc功能"),
            Map.of("name", "标准网络", "delay", 10, "loss", 0, "bandwidth", 0, "description", "正常网络环境"),
            Map.of("name", "政务跨域波动", "delay", 100, "loss", 5, "bandwidth", 10, "description", "跨域网络波动场景"),
            Map.of("name", "普通弱网", "delay", 250, "loss", 15, "bandwidth", 2, "description", "中等弱网环境"),
            Map.of("name", "极端弱网", "delay", 500, "loss", 30, "bandwidth", 1, "description", "极端网络条件")
        ));
    }
}
