package com.femstep.store.controller;

import com.femstep.store.dto.catalog.CategoryResponse;
import com.femstep.store.dto.catalog.PageResponse;
import com.femstep.store.dto.catalog.ProductDetailResponse;
import com.femstep.store.dto.catalog.ProductSummaryResponse;
import com.femstep.store.service.CatalogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/public")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> categories() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(catalogService.listCategories());
    }

    @GetMapping("/products")
    public ResponseEntity<PageResponse<ProductSummaryResponse>> products(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) List<Integer> sizes,
            @RequestParam(required = false) List<String> colors,
            @RequestParam(required = false) String material,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false, defaultValue = "true") Boolean inStock,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(48) int size,
            @RequestParam(defaultValue = "featured") String sort
    ) {
        return ResponseEntity.ok(catalogService.listProducts(
                q, category, minPrice, maxPrice, sizes, colors, material,
                featured, inStock, page, size, sort
        ));
    }

    @GetMapping("/products/featured")
    public ResponseEntity<List<ProductSummaryResponse>> featured(
            @RequestParam(defaultValue = "8") @Min(1) @Max(12) int limit
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(catalogService.featuredProducts(limit));
    }

    @GetMapping("/products/{slug}")
    public ResponseEntity<ProductDetailResponse> product(@PathVariable String slug) {
        return ResponseEntity.ok(catalogService.getProduct(slug));
    }
}
