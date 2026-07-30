package com.femstep.store.service;

import com.femstep.store.dto.address.AddressRequest;
import com.femstep.store.dto.address.AddressResponse;
import com.femstep.store.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AddressService {

    private final JdbcTemplate jdbcTemplate;

    public AddressService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(String email) {
        return jdbcTemplate.query("""
                        SELECT a.*
                        FROM addresses a
                        JOIN users u ON u.id = a.user_id
                        WHERE lower(u.email) = lower(?)
                        ORDER BY a.default_address DESC, a.created_at, a.id
                        """,
                (rs, rowNum) -> new AddressResponse(
                        rs.getLong("id"),
                        rs.getString("label"),
                        rs.getString("recipient_name"),
                        rs.getString("postal_code"),
                        rs.getString("street"),
                        rs.getString("number"),
                        rs.getString("complement"),
                        rs.getString("neighborhood"),
                        rs.getString("city"),
                        rs.getString("state"),
                        rs.getString("phone"),
                        rs.getBoolean("default_address")
                ),
                email
        );
    }

    @Transactional
    public AddressResponse create(String email, AddressRequest request) {
        Long userId = userId(email);
        boolean makeDefault = request.defaultAddress()
                || jdbcTemplate.queryForObject(
                    "SELECT count(*) = 0 FROM addresses WHERE user_id = ?",
                    Boolean.class,
                    userId
                );
        if (makeDefault) {
            clearDefault(userId);
        }
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO addresses (
                    user_id, label, recipient_name, postal_code, street,
                    number, complement, neighborhood, city, state,
                    phone, default_address
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                userId,
                request.label().trim(),
                request.recipientName().trim(),
                request.postalCode(),
                request.street().trim(),
                request.number().trim(),
                blankToNull(request.complement()),
                request.neighborhood().trim(),
                request.city().trim(),
                request.state().toUpperCase(Locale.ROOT),
                blankToNull(request.phone()),
                makeDefault
        );
        return get(userId, id);
    }

    @Transactional
    public AddressResponse update(String email, Long addressId, AddressRequest request) {
        Long userId = userId(email);
        get(userId, addressId);
        if (request.defaultAddress()) {
            clearDefault(userId);
        }
        jdbcTemplate.update("""
                UPDATE addresses
                SET label = ?, recipient_name = ?, postal_code = ?, street = ?,
                    number = ?, complement = ?, neighborhood = ?, city = ?,
                    state = ?, phone = ?, default_address = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND user_id = ?
                """,
                request.label().trim(),
                request.recipientName().trim(),
                request.postalCode(),
                request.street().trim(),
                request.number().trim(),
                blankToNull(request.complement()),
                request.neighborhood().trim(),
                request.city().trim(),
                request.state().toUpperCase(Locale.ROOT),
                blankToNull(request.phone()),
                request.defaultAddress(),
                addressId,
                userId
        );
        ensureDefaultExists(userId);
        return get(userId, addressId);
    }

    @Transactional
    public void delete(String email, Long addressId) {
        Long userId = userId(email);
        int deleted = jdbcTemplate.update(
                "DELETE FROM addresses WHERE id = ? AND user_id = ?",
                addressId,
                userId
        );
        if (deleted != 1) {
            throw new ResourceNotFoundException("Endereço não encontrado");
        }
        ensureDefaultExists(userId);
    }

    @Transactional
    public AddressResponse makeDefault(String email, Long addressId) {
        Long userId = userId(email);
        get(userId, addressId);
        clearDefault(userId);
        jdbcTemplate.update(
                "UPDATE addresses SET default_address = true, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                addressId
        );
        return get(userId, addressId);
    }

    private AddressResponse get(Long userId, Long addressId) {
        return jdbcTemplate.query("""
                        SELECT * FROM addresses WHERE id = ? AND user_id = ?
                        """,
                (rs, rowNum) -> new AddressResponse(
                        rs.getLong("id"),
                        rs.getString("label"),
                        rs.getString("recipient_name"),
                        rs.getString("postal_code"),
                        rs.getString("street"),
                        rs.getString("number"),
                        rs.getString("complement"),
                        rs.getString("neighborhood"),
                        rs.getString("city"),
                        rs.getString("state"),
                        rs.getString("phone"),
                        rs.getBoolean("default_address")
                ),
                addressId,
                userId
        ).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Endereço não encontrado"));
    }

    private Long userId(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE lower(email) = lower(?) AND active = true",
                Long.class,
                email
        );
    }

    private void clearDefault(Long userId) {
        jdbcTemplate.update(
                "UPDATE addresses SET default_address = false, updated_at = CURRENT_TIMESTAMP WHERE user_id = ? AND default_address = true",
                userId
        );
    }

    private void ensureDefaultExists(Long userId) {
        Boolean hasDefault = jdbcTemplate.queryForObject(
                "SELECT count(*) FILTER (WHERE default_address) > 0 FROM addresses WHERE user_id = ?",
                Boolean.class,
                userId
        );
        if (Boolean.FALSE.equals(hasDefault)) {
            jdbcTemplate.update("""
                    UPDATE addresses SET default_address = true
                    WHERE id = (
                        SELECT id FROM addresses WHERE user_id = ?
                        ORDER BY created_at, id LIMIT 1
                    )
                    """, userId);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
