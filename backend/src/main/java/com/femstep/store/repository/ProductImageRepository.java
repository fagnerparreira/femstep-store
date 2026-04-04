package com.femstep.store.repository;

import com.femstep.store.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    
    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(Long productId);
    
    Optional<ProductImage> findByProductIdAndMainImageTrue(Long productId);
    
    List<ProductImage> findByProductIdOrderByMainImageDescDisplayOrderAsc(Long productId);
}
