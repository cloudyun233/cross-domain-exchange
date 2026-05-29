package com.cde.security;

import com.cde.dto.LoginResponse;
import com.cde.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtSecurityTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AuthService authService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void jwtUtilGeneratesParsesAndRejectsInvalidTokens() {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", "short-secret");
        ReflectionTestUtils.setField(util, "expiration", 60_000L);

        String token = util.generateToken("alice", "alice_001", "gov/tax", "producer");

        assertThat(util.validateToken(token)).isTrue();
        assertThat(util.getUsernameFromToken(token)).isEqualTo("alice");
        assertThat(util.getClientIdFromToken(token)).isEqualTo("alice_001");
        assertThat(util.getDomainCodeFromToken(token)).isEqualTo("gov/tax");
        assertThat(util.getRoleTypeFromToken(token)).isEqualTo("producer");
        assertThat(util.getExpirationMs()).isEqualTo(60_000L);
        assertThat(util.validateToken(token + "bad")).isFalse();
    }

    @Test
    void filterPassesThroughWhenNoTokenIsPresent() throws ServletException, IOException {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, authService, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void filterRejectsInvalidTokenBeforeCallingChain() throws ServletException, IOException {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, authService, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtUtil.validateToken("bad")).thenReturn(false);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
    }

    @Test
    void filterSkipsLoginRequestsEvenWhenAStaleTokenIsPresent() throws ServletException, IOException {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, authService, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("Authorization", "Bearer stale");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(jwtUtil, authService);
    }

    @Test
    void filterAuthenticatesCurrentUserWhenClaimsStillMatch() throws ServletException, IOException {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, authService, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("token", "good");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtUtil.validateToken("good")).thenReturn(true);
        when(jwtUtil.getUsernameFromToken("good")).thenReturn("alice");
        when(jwtUtil.getRoleTypeFromToken("good")).thenReturn("producer");
        when(jwtUtil.getClientIdFromToken("good")).thenReturn("alice_001");
        when(jwtUtil.getDomainCodeFromToken("good")).thenReturn("gov");
        when(authService.getCurrentUserProfile("alice", "good")).thenReturn(LoginResponse.builder()
                .username("alice")
                .roleType("producer")
                .clientId("alice_001")
                .domainCode("gov")
                .build());

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_PRODUCER");
    }

    @Test
    void filterRejectsMissingRoleAndChangedCurrentUserState() throws ServletException, IOException {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, authService, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("access_token", "missing-role");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtUtil.validateToken("missing-role")).thenReturn(true);
        when(jwtUtil.getUsernameFromToken("missing-role")).thenReturn("alice");
        when(jwtUtil.getRoleTypeFromToken("missing-role")).thenReturn("");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        MockHttpServletRequest changed = new MockHttpServletRequest();
        changed.addHeader("Authorization", "Bearer changed");
        MockHttpServletResponse changedResponse = new MockHttpServletResponse();
        when(jwtUtil.validateToken("changed")).thenReturn(true);
        when(jwtUtil.getUsernameFromToken("changed")).thenReturn("alice");
        when(jwtUtil.getRoleTypeFromToken("changed")).thenReturn("producer");
        when(jwtUtil.getClientIdFromToken("changed")).thenReturn("old");
        when(authService.getCurrentUserProfile("alice", "changed")).thenReturn(LoginResponse.builder()
                .username("alice")
                .roleType("producer")
                .clientId("new")
                .domainCode("gov")
                .build());

        filter.doFilter(changed, changedResponse, new MockFilterChain());

        assertThat(changedResponse.getStatus()).isEqualTo(401);
        verify(authService).getCurrentUserProfile("alice", "changed");
    }
}
