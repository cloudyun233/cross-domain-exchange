package com.cde.config;

import com.cde.mqtt.EmqxApiClient;
import com.cde.service.AclService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Mirrors database ACL rules into EMQX built-in authorization storage on
 * backend startup.
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
