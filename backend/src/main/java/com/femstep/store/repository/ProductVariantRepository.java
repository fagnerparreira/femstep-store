package com.femstep.store.repository;

import com.femstep.store.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    
    Optional<ProductVariant> findBySku(String sku);
    
    boolean existsBySku(String sku);
    
    List<ProductVariant> findByProductIdAndActiveTrueOrderBySizeAscColorAsc(Long productId);
    
    Optional<ProductVariant> findByProductIdAndSizeAndColorAndActiveTrue(Long productId, Integer size, String color);
    
    List<ProductVariant> findByProductIdAndActiveTrueAndStockQuantityGreaterThanOrderBySizeAscColorAsc(Long productId, Integer minStock);
}
