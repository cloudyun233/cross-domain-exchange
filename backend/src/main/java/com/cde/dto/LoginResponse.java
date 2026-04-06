package com.cde.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private long expires;
    private String username;
    private String roleType;
    private String roleName;
    private String domainCode;
    private String domainName;
    private String clientId;
}
