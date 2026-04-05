package com.cde.controller;

import com.cde.dto.StatusResponse;
import com.cde.mqtt.EmqxApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/status")
@RequiredArgsConstructor
public class StatusController {

    private final EmqxApiClient emqxApiClient;

    @GetMapping("/backend")
    public StatusResponse status() {
        return new StatusResponse("ok");
    }

    @GetMapping("/emqx")
    public StatusResponse emqxStatus() {
        try {
            boolean online = emqxApiClient.isApiReady();
            return new StatusResponse(online ? "online" : "offline");
        } catch (Exception e) {
            return new StatusResponse("offline");
        }
    }
}
