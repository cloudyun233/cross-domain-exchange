package com.cde.config;

import com.cde.mqtt.EmqxApiClient;
import com.cde.service.AclService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动时将数据库中的 ACL 规则同步至 EMQX 内置授权存储。
 *
 * <p><b>为什么需要启动同步：</b>EMQX 内置数据库（built-in database）为易失性存储，
 * 容器重启后 ACL 规则会丢失，因此每次后端启动时必须重新写入，确保 MQTT 客户端
 * 的发布/订阅权限与业务数据库保持一致。
 *
 * <p><b>重试策略：</b>EMQX 服务可能晚于后端启动，因此采用轮询等待机制——
 * 最多尝试 10 次，每次间隔 2 秒（总计约 20 秒），直到 EMQX HTTP API 可用。
 *
 * <p><b>同步失败的影响：</b>若 10 次尝试后 EMQX API 仍不可用或同步过程抛出异常，
 * 仅记录警告日志，不阻止应用启动。此时 MQTT 客户端将无法通过 ACL 校验，
 * 需人工介入，或调用 API 接口全量同步ACL 规则。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmqxAclSyncInitializer implements ApplicationRunner {

    private final AclService aclService;
    private final EmqxApiClient emqxApiClient;

    @Override
    public void run(ApplicationArguments args) {
        try {
            for (int attempt = 1; attempt <= 10; attempt++) {
                if (emqxApiClient.isApiReady()) {
                    aclService.syncToEmqx();
                    log.info("Initial ACL sync to EMQX completed on attempt {}", attempt);
                    return;
                }

                log.info("Waiting for EMQX HTTP API to become ready, attempt {}", attempt);
                Thread.sleep(2000);
            }
            log.warn("Initial ACL sync skipped because EMQX HTTP API never became ready");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Initial ACL sync interrupted");
        } catch (Exception e) {
            log.warn("Initial ACL sync to EMQX failed: {}", e.getMessage());
        }
    }
}
