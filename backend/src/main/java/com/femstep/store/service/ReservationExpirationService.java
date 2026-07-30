package com.femstep.store.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReservationExpirationService {

    private final JdbcTemplate jdbcTemplate;

    public ReservationExpirationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${orders.reservation-cleanup-ms:60000}")
    @Transactional
    public void releaseExpiredReservations() {
        List<ExpiredReservation> reservations = jdbcTemplate.query("""
                        SELECT sr.id, sr.order_id, sr.variant_id, sr.quantity
                        FROM stock_reservations sr
                        WHERE sr.status = 'ACTIVE' AND sr.expires_at <= CURRENT_TIMESTAMP
                        ORDER BY sr.id
                        FOR UPDATE SKIP LOCKED
                        """,
                (rs, rowNum) -> new ExpiredReservation(
                        rs.getLong("id"),
                        rs.getLong("order_id"),
                        rs.getLong("variant_id"),
                        rs.getInt("quantity")
                )
        );
        for (ExpiredReservation reservation : reservations) {
            jdbcTemplate.update("""
                    UPDATE product_variants
                    SET reserved_quantity = GREATEST(reserved_quantity - ?, 0),
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, reservation.quantity(), reservation.variantId());
            jdbcTemplate.update("""
                    UPDATE stock_reservations
                    SET status = 'EXPIRED', released_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, reservation.id());
            jdbcTemplate.update("""
                    UPDATE payments
                    SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                    WHERE order_id = ? AND status = 'PENDING'
                    """, reservation.orderId());
            jdbcTemplate.update(
                    "DELETE FROM coupon_redemptions WHERE order_id = ?",
                    reservation.orderId()
            );
            int changed = jdbcTemplate.update("""
                    UPDATE orders
                    SET status = 'PAYMENT_FAILED', payment_status = 'EXPIRED',
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'PENDING_PAYMENT'
                    """, reservation.orderId());
            if (changed == 1) {
                jdbcTemplate.update("""
                        INSERT INTO order_status_history (
                            order_id, previous_status, new_status, reason
                        ) VALUES (?, 'PENDING_PAYMENT', 'PAYMENT_FAILED', 'Reserva expirada sem pagamento')
                        """, reservation.orderId());
            }
        }
    }

    private record ExpiredReservation(Long id, Long orderId, Long variantId, int quantity) {
    }
}
