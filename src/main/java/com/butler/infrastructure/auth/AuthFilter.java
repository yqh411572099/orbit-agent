package com.butler.infrastructure.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 校验 Authorization: Bearer <session token>，放行登录/注册与静态资源。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_API = Set.of(
            "/api/users/login",
            "/api/users/register"
    );

    private final SessionTokenService tokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthFilter(SessionTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        if (!path.startsWith("/api/") || PUBLIC_API.contains(path)) {
            chain.doFilter(req, resp);
            return;
        }
        String header = req.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer "))
                ? header.substring(7).trim() : null;
        SessionTokenService.SessionPayload payload = tokenService.validate(token);
        if (payload == null) {
            unauthorized(resp);
            return;
        }
        CurrentUser.set(payload);
        try {
            chain.doFilter(req, resp);
        } finally {
            CurrentUser.clear();
        }
    }

    private void unauthorized(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "status", 401, "error", "Unauthorized", "message", "登录已过期，请重新登录")));
    }
}
