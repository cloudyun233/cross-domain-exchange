package com.cde.controller;

import com.cde.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/network")
@PreAuthorize("hasRole('ADMIN')")
public class NetworkController {

    @PostMapping("/simulate")
    public ApiResponse<String> simulate(
            @RequestParam(defaultValue = "0") int delayMs,
            @RequestParam(defaultValue = "0") int lossPercent,
            @RequestParam(defaultValue = "0") int bandwidthMbps) {
        try {
            String cmd;
            if (delayMs == 0 && lossPercent == 0 && bandwidthMbps == 0) {
                cmd = "tc qdisc del dev eth0 root 2>/dev/null; echo 'Network reset'";
                log.info("弱网模拟已重置: 无限制模式");
            } else {
                cmd = String.format(
                    "tc qdisc replace dev eth0 root netem delay %dms loss %d%%%s",
                    delayMs, lossPercent,
                    bandwidthMbps > 0 ? String.format(" rate %dmbit", bandwidthMbps) : "");
                log.info("弱网模拟命令: {}", cmd);
            }

            ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            String scenario = getScenarioName(delayMs, lossPercent, bandwidthMbps);
            return ApiResponse.ok(String.format("弱网模拟已设置: %s", scenario), null);
        } catch (Exception e) {
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
