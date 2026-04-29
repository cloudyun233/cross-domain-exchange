package com.cde.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT utility for backend login and broker authentication.
 * JWT载荷包含: username, clientId, domainCode, roleType, exp, iat, sub
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    /**
     * 构建签名密钥。
     * <p>
     * 将配置的密钥字符串转为 UTF-8 字节，若长度不足 32 字节（HMAC-SHA256 最低要求），
     * 则以零字节右填充至 32 字节，避免密钥过短导致签名失败。
     *
     * @return HMAC-SHA256 签名密钥
     */
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
     * 生成 JWT 令牌。
     * <p>
     * 载荷结构：
     * <ul>
     *   <li>sub (subject): 用户名</li>
     *   <li>username: 用户名（与 sub 一致，保留用于兼容）</li>
     *   <li>clientId: MQTT ClientID，用于连接标识和持久会话信息</li>
     *   <li>domainCode: 所属安全域编码，对应 MQTT 主题路径段</li>
     *   <li>roleType: 角色类型 (producer/consumer/admin)</li>
     *   <li>iat: 签发时间</li>
     *   <li>exp: 过期时间，由 jwt.expiration 配置决定</li>
     * </ul>
     *
     * @param username   用户名
     * @param clientId   MQTT 客户端标识
     * @param domainCode 安全域编码
     * @param roleType   角色类型
     * @return 签名后的 JWT 令牌字符串
     */
    public String generateToken(String username, String clientId, String domainCode, String roleType) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("clientId", clientId);
        claims.put("domainCode", domainCode);
        claims.put("roleType", roleType);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public String getClientIdFromToken(String token) {
        return getClaims(token).get("clientId", String.class);
    }

    public String getDomainCodeFromToken(String token) {
        return getClaims(token).get("domainCode", String.class);
    }

    public String getRoleTypeFromToken(String token) {
        return getClaims(token).get("roleType", String.class);
    }

    /**
     * 验证令牌有效性。
     * <p>
     * 通过尝试解析令牌来验证签名和有效期，解析失败（签名篡改、过期、格式错误等）返回 false。
     *
     * @param token 待验证的 JWT 令牌
     * @return 令牌有效返回 true，否则返回 false
     */
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
