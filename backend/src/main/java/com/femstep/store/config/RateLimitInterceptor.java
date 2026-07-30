package com.femstep.store.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Map<String, Limit> LIMITS = Map.of(
            "/api/auth/login", new Limit("LOGIN", 10, 300),
            "/api/auth/register", new Limit("REGISTER", 5, 900),
            "/api/auth/forgot-password", new Limit("PASSWORD_RESET", 5, 900),
            "/api/orders", new Limit("CHECKOUT", 20, 300)
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RateLimitInterceptor(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {
        if (!request.getMethod().equals("POST")) {
            return true;
        }
        Limit limit = LIMITS.get(request.getRequestURI());
        if (limit == null) {
            return true;
        }

        String identity = request.getUserPrincipal() == null
                ? "ip:" + request.getRemoteAddr()
                : "user:" + request.getUserPrincipal().getName();
        LocalDateTime windowThreshold = LocalDateTime.now().minusSeconds(limit.windowSeconds());
        Integer requests = jdbcTemplate.queryForObject("""
                INSERT INTO api_rate_limits (
                    key_hash, endpoint_group, window_started_at, requests
                ) VALUES (?, ?, CURRENT_TIMESTAMP, 1)
                ON CONFLICT (key_hash, endpoint_group) DO UPDATE
                SET window_started_at = CASE
                        WHEN api_rate_limits.window_started_at < ?
                        THEN CURRENT_TIMESTAMP
                        ELSE api_rate_limits.window_started_at
                    END,
                    requests = CASE
                        WHEN api_rate_limits.window_started_at < ? THEN 1
                        ELSE api_rate_limits.requests + 1
                    END,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING requests
                """,
                Integer.class,
                hash(identity),
                limit.group(),
                windowThreshold,
                windowThreshold
        );
        if (requests != null && requests > limit.maxRequests()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(limit.windowSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                    response.getWriter(),
                    Map.of("message", "Muitas tentativas. Aguarde alguns minutos e tente novamente.")
            );
            return false;
        }
        return true;
    }

    @Scheduled(cron = "0 20 * * * *")
    public void removeExpiredEntries() {
        jdbcTemplate.update(
                "DELETE FROM api_rate_limits WHERE updated_at < CURRENT_TIMESTAMP - INTERVAL '2 days'"
        );
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível", exception);
        }
    }

    private record Limit(String group, int maxRequests, int windowSeconds) {
    }
}
