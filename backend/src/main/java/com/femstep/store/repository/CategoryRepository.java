package com.femstep.store.repository;

import com.femstep.store.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    
    Optional<Category> findBySlug(String slug);
    
    boolean existsBySlug(String slug);
    
    List<Category> findByActiveTrueOrderByCreatedAt();
    
    List<Category> findByParentCategoryIsNullAndActiveTrueOrderByCreatedAt();
    
    List<Category> findByParentCategoryIdAndActiveTrueOrderByCreatedAt(Long parentCategoryId);
}
