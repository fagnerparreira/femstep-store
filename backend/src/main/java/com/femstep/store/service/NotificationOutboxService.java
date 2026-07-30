package com.femstep.store.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationOutboxService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public NotificationOutboxService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void enqueueOrderEvent(String eventType, Long orderId, Long userId) {
        String eventKey = "ORDER:" + orderId + ":" + eventType;
        String payload = "{\"orderId\":" + orderId + ",\"userId\":" + userId + "}";
        jdbcTemplate.update("""
                INSERT INTO notification_outbox (
                    event_key, event_type, aggregate_type, aggregate_id, payload_json
                ) VALUES (?, ?, 'ORDER', ?, ?)
                ON CONFLICT (event_key) DO NOTHING
                """,
                eventKey,
                eventType,
                orderId.toString(),
                payload
        );
    }

    public void enqueueAccountTokenEvent(
            String eventType,
            Long userId,
            Long tokenId,
            String email,
            String rawToken
    ) {
        String eventKey = "USER:" + userId + ":" + eventType + ":" + tokenId;
        String payload = json(Map.of(
                "userId", userId,
                "email", email,
                "token", rawToken
        ));
        jdbcTemplate.update("""
                INSERT INTO notification_outbox (
                    event_key, event_type, aggregate_type, aggregate_id, payload_json
                ) VALUES (?, ?, 'USER', ?, ?)
                ON CONFLICT (event_key) DO NOTHING
                """,
                eventKey,
                eventType,
                userId.toString(),
                payload
        );
    }

    public void redactAccountTokenEvent(
            String eventType,
            Long userId,
            Long tokenId
    ) {
        jdbcTemplate.update("""
                UPDATE notification_outbox
                SET payload_json = '{}', updated_at = CURRENT_TIMESTAMP
                WHERE event_key = ?
                """,
                "USER:" + userId + ":" + eventType + ":" + tokenId
        );
    }

    private String json(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível preparar a notificação", exception);
        }
    }
}
