package com.femstep.store.service;

import com.femstep.store.dto.catalog.ProductSummaryResponse;
import com.femstep.store.exception.ResourceNotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FavoriteService {

    private final JdbcTemplate jdbcTemplate;
    private final CatalogService catalogService;

    public FavoriteService(JdbcTemplate jdbcTemplate, CatalogService catalogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.catalogService = catalogService;
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> list(String email) {
        List<Long> productIds = jdbcTemplate.queryForList("""
                SELECT f.product_id
                FROM favorites f
                JOIN users u ON u.id = f.user_id
                JOIN products p ON p.id = f.product_id
                WHERE lower(u.email) = lower(?) AND p.active = true
                ORDER BY f.created_at DESC, f.id DESC
                """, Long.class, email);
        return catalogService.summariesByIds(productIds);
    }

    @Transactional
    public void add(String email, Long productId) {
        Long userId = userId(email);
        Boolean available = jdbcTemplate.queryForObject(
                "SELECT count(*) > 0 FROM products WHERE id = ? AND active = true",
                Boolean.class,
                productId
        );
        if (!Boolean.TRUE.equals(available)) {
            throw new ResourceNotFoundException("Produto não encontrado");
        }

        try {
            jdbcTemplate.update(
                    "INSERT INTO favorites (user_id, product_id) VALUES (?, ?)",
                    userId,
                    productId
            );
        } catch (DuplicateKeyException ignored) {
            // Repetir a mesma ação mantém o recurso no estado desejado.
        }
    }

    @Transactional
    public void remove(String email, Long productId) {
        jdbcTemplate.update(
                "DELETE FROM favorites WHERE user_id = ? AND product_id = ?",
                userId(email),
                productId
        );
    }

    @Transactional(readOnly = true)
    public boolean contains(String email, Long productId) {
        Boolean favorite = jdbcTemplate.queryForObject("""
                SELECT count(*) > 0
                FROM favorites f
                JOIN users u ON u.id = f.user_id
                WHERE lower(u.email) = lower(?) AND f.product_id = ?
                """, Boolean.class, email, productId);
        return Boolean.TRUE.equals(favorite);
    }

    private Long userId(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE lower(email) = lower(?) AND active = true",
                Long.class,
                email
        );
    }
}
