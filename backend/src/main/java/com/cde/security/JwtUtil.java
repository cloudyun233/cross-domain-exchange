package com.cde.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类 (论文4.2.1: 无状态认证机制)
 * 生成的JWT令牌同时用于:
 * 1. Spring Boot REST API认证
 * 2. EMQX MQTT连接认证 (作为CONNECT报文的password字段)
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成JWT令牌
     * Payload: clientId, domainCode, roleType, exp
     */
    public String generateToken(String clientId, String domainCode, String roleType) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("clientId", clientId);
        claims.put("domainCode", domainCode);
        claims.put("roleType", roleType);

        return Jwts.builder()
                .claims(claims)
                .subject(clientId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String getClientIdFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public String getDomainCodeFromToken(String token) {
        return getClaims(token).get("domainCode", String.class);
    }

    public String getRoleTypeFromToken(String token) {
        return getClaims(token).get("roleType", String.class);
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getExpirationMs() {
        return expiration;
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
