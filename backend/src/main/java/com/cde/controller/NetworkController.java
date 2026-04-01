package com.cde.controller;

import com.cde.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * 弱网模拟控制 (论文表4-4: tc弱网参数)
 * 调用Linux TC命令模拟跨域弱网环境
 */
@Slf4j
@RestController
@RequestMapping("/api/network")
public class NetworkController {

    @PostMapping("/simulate")
    public ApiResponse<String> simulate(
            @RequestParam(defaultValue = "0") int delayMs,
            @RequestParam(defaultValue = "0") int lossPercent,
            @RequestParam(defaultValue = "0") int bandwidthMbps) {
        try {
            String cmd;
            if (delayMs == 0 && lossPercent == 0) {
                cmd = "tc qdisc del dev eth0 root 2>/dev/null; echo 'Network reset'";
            } else {
                cmd = String.format(
                    "tc qdisc replace dev eth0 root netem delay %dms loss %d%%%s",
                    delayMs, lossPercent,
                    bandwidthMbps > 0 ? String.format(" rate %dmbit", bandwidthMbps) : "");
            }
            log.info("弱网模拟命令: {}", cmd);

            ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            String scenario = delayMs == 0 ? "标准网络" :
                    delayMs <= 100 ? "政务跨域波动网络" : "极端跨域弱网";

            return ApiResponse.ok(String.format("弱网模拟已设置: %s (延迟=%dms, 丢包=%d%%)",
                    scenario, delayMs, lossPercent), null);
        } catch (Exception e) {
            return ApiResponse.fail("弱网模拟设置失败(需要在Linux Docker环境中运行): " + e.getMessage());
        }
    }

    @GetMapping("/presets")
    public ApiResponse<Object> getPresets() {
        return ApiResponse.ok(java.util.List.of(
            Map.of("name", "标准网络", "delay", 10, "loss", 0, "bandwidth", 0),
            Map.of("name", "政务跨域波动", "delay", 100, "loss", 5, "bandwidth", 10),
            Map.of("name", "极端弱网", "delay", 500, "loss", 20, "bandwidth", 1)
        ));
    }
}
