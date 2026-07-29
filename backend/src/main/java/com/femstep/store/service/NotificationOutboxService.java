package com.femstep.store.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationOutboxService {
    private final JdbcTemplate jdbcTemplate;

    public NotificationOutboxService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
}
