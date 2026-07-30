package com.femstep.store.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class NotificationOutboxStore {

    private static final int MAX_ATTEMPTS = 5;
    private final JdbcTemplate jdbcTemplate;

    public NotificationOutboxStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<OutboxMessage> claimBatch() {
        return jdbcTemplate.query("""
                        UPDATE notification_outbox
                        SET status = 'PROCESSING', attempts = attempts + 1,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id IN (
                            SELECT id
                            FROM notification_outbox
                            WHERE attempts < ?
                              AND available_at <= CURRENT_TIMESTAMP
                              AND (
                                status = 'PENDING'
                                OR (status = 'PROCESSING'
                                    AND updated_at < CURRENT_TIMESTAMP - INTERVAL '10 minutes')
                              )
                            ORDER BY id
                            FOR UPDATE SKIP LOCKED
                            LIMIT 10
                        )
                        RETURNING id, event_type, aggregate_type, aggregate_id,
                                  payload_json, attempts
                        """,
                (rs, rowNum) -> new OutboxMessage(
                        rs.getLong("id"),
                        rs.getString("event_type"),
                        rs.getString("aggregate_type"),
                        rs.getString("aggregate_id"),
                        rs.getString("payload_json"),
                        rs.getInt("attempts")
                ),
                MAX_ATTEMPTS
        );
    }

    public void markSent(Long messageId, boolean redactPayload) {
        jdbcTemplate.update("""
                UPDATE notification_outbox
                SET status = 'SENT', processed_at = CURRENT_TIMESTAMP,
                    last_error = NULL,
                    payload_json = CASE WHEN ? THEN '{}' ELSE payload_json END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                redactPayload,
                messageId
        );
    }

    public void markFailed(OutboxMessage message, String error) {
        boolean exhausted = message.attempts() >= MAX_ATTEMPTS;
        int delaySeconds = Math.min(3600, 30 * (1 << Math.min(message.attempts(), 6)));
        jdbcTemplate.update("""
                UPDATE notification_outbox
                SET status = ?, available_at = ?, last_error = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                exhausted ? "FAILED" : "PENDING",
                LocalDateTime.now().plusSeconds(delaySeconds),
                truncate(error, 1000),
                message.id()
        );
    }

    private String truncate(String value, int maxLength) {
        String safe = value == null || value.isBlank()
                ? "Falha não detalhada pelo provedor"
                : value.replaceAll("[\\r\\n]+", " ");
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    public record OutboxMessage(
            Long id,
            String eventType,
            String aggregateType,
            String aggregateId,
            String payloadJson,
            int attempts
    ) {
    }
}
