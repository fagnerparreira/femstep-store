package com.femstep.store.service;

import com.femstep.store.domain.Category;
import com.femstep.store.domain.Product;
import com.femstep.store.domain.ProductImage;
import com.femstep.store.domain.ProductVariant;
import com.femstep.store.dto.catalog.CategoryResponse;
import com.femstep.store.dto.catalog.PageResponse;
import com.femstep.store.dto.catalog.ProductDetailResponse;
import com.femstep.store.dto.catalog.ProductImageResponse;
import com.femstep.store.dto.catalog.ProductSummaryResponse;
import com.femstep.store.dto.catalog.ProductVariantResponse;
import com.femstep.store.exception.ResourceNotFoundException;
import com.femstep.store.repository.CategoryRepository;
import com.femstep.store.repository.ProductRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private static final int MAX_PAGE_SIZE = 48;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public CatalogService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryResponse> listCategories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    public PageResponse<ProductSummaryResponse> listProducts(
            String search,
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<Integer> sizes,
            List<String> colors,
            String material,
            Boolean featured,
            Boolean inStock,
            int page,
            int size,
            String sort
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, resolveSort(sort));

        Specification<Product> specification = buildSpecification(
                search, category, minPrice, maxPrice, sizes, colors, material, featured, inStock
        );

        Page<ProductSummaryResponse> result = productRepository
                .findAll(specification, pageable)
                .map(this::toSummary);

        return PageResponse.from(result);
    }

    public List<ProductSummaryResponse> featuredProducts(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 12);
        return productRepository.findAll(
                        buildSpecification(null, null, null, null, null, null, null, true, true),
                        PageRequest.of(0, safeLimit, Sort.by(Sort.Order.desc("createdAt")))
                )
                .map(this::toSummary)
                .getContent();
    }

    public ProductDetailResponse getProduct(String slug) {
        Product product = productRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        List<ProductVariantResponse> variants = activeVariants(product).stream()
                .map(this::toVariantResponse)
                .toList();

        List<ProductImageResponse> images = sortedImages(product).stream()
                .map(image -> new ProductImageResponse(
                        image.getId(),
                        image.getUrl(),
                        image.getAltText(),
                        image.getDisplayOrder(),
                        image.getMainImage()
                ))
                .toList();

        return new ProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getShortDescription(),
                product.getBasePrice(),
                product.getCompareAtPrice(),
                product.getBrand(),
                product.getMaterial(),
                product.getCategory().getName(),
                product.getCategory().getSlug(),
                product.getFeatured(),
                variants,
                images
        );
    }

    public List<ProductSummaryResponse> summariesByIds(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }

        var order = new java.util.HashMap<Long, Integer>();
        for (int index = 0; index < productIds.size(); index++) {
            order.put(productIds.get(index), index);
        }

        return productRepository.findAllById(productIds).stream()
                .filter(Product::getActive)
                .sorted(Comparator.comparingInt(product -> order.getOrDefault(product.getId(), Integer.MAX_VALUE)))
                .map(this::toSummary)
                .toList();
    }

    private Specification<Product> buildSpecification(
            String search,
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<Integer> sizes,
            List<String> colors,
            String material,
            Boolean featured,
            Boolean inStock
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isTrue(root.get("active")));

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern),
                        builder.like(builder.lower(root.get("material")), pattern)
                ));
            }

            if (category != null && !category.isBlank()) {
                predicates.add(builder.equal(root.join("category").get("slug"), category));
            }
            if (minPrice != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("basePrice"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("basePrice"), maxPrice));
            }
            if (material != null && !material.isBlank()) {
                predicates.add(builder.equal(
                        builder.lower(root.get("material")),
                        material.trim().toLowerCase(Locale.ROOT)
                ));
            }
            if (featured != null) {
                predicates.add(builder.equal(root.get("featured"), featured));
            }

            boolean filterVariants = (sizes != null && !sizes.isEmpty())
                    || (colors != null && !colors.isEmpty())
                    || Boolean.TRUE.equals(inStock);

            if (filterVariants) {
                Join<Product, ProductVariant> variants = root.join("variants", JoinType.INNER);
                predicates.add(builder.isTrue(variants.get("active")));
                if (sizes != null && !sizes.isEmpty()) {
                    predicates.add(variants.get("size").in(sizes));
                }
                if (colors != null && !colors.isEmpty()) {
                    List<String> normalizedColors = colors.stream()
                            .filter(color -> color != null && !color.isBlank())
                            .map(String::trim)
                            .toList();
                    if (!normalizedColors.isEmpty()) {
                        predicates.add(variants.get("color").in(normalizedColors));
                    }
                }
                if (Boolean.TRUE.equals(inStock)) {
                    predicates.add(builder.greaterThan(
                            builder.diff(variants.get("stockQuantity"), variants.get("reservedQuantity")),
                            0
                    ));
                }
                query.distinct(true);
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Sort resolveSort(String sort) {
        return switch (sort == null ? "featured" : sort) {
            case "price-asc" -> Sort.by(Sort.Order.asc("basePrice"), Sort.Order.asc("name"));
            case "price-desc" -> Sort.by(Sort.Order.desc("basePrice"), Sort.Order.asc("name"));
            case "name" -> Sort.by(Sort.Order.asc("name"));
            case "newest" -> Sort.by(Sort.Order.desc("createdAt"));
            default -> Sort.by(Sort.Order.desc("featured"), Sort.Order.desc("createdAt"));
        };
    }

    private CategoryResponse toCategoryResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription()
        );
    }

    private ProductSummaryResponse toSummary(Product product) {
        List<ProductVariant> variants = activeVariants(product);
        ProductImage mainImage = sortedImages(product).stream().findFirst().orElse(null);
        List<Integer> sizes = variants.stream()
                .filter(variant -> variant.getAvailableQuantity() > 0)
                .map(ProductVariant::getSize)
                .distinct()
                .sorted()
                .toList();
        List<String> colors = variants.stream()
                .filter(variant -> variant.getAvailableQuantity() > 0)
                .map(ProductVariant::getColor)
                .distinct()
                .sorted()
                .toList();
        int availableStock = variants.stream()
                .mapToInt(ProductVariant::getAvailableQuantity)
                .sum();

        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getShortDescription(),
                product.getBasePrice(),
                product.getCompareAtPrice(),
                mainImage == null ? null : mainImage.getUrl(),
                mainImage == null ? product.getName() : mainImage.getAltText(),
                product.getCategory().getName(),
                product.getCategory().getSlug(),
                product.getMaterial(),
                product.getFeatured(),
                product.getCompareAtPrice() != null
                        && product.getCompareAtPrice().compareTo(product.getBasePrice()) > 0,
                sizes,
                colors,
                availableStock
        );
    }

    private List<ProductVariant> activeVariants(Product product) {
        return product.getVariants().stream()
                .filter(ProductVariant::getActive)
                .sorted(Comparator
                        .comparing(ProductVariant::getSize)
                        .thenComparing(ProductVariant::getColor))
                .toList();
    }

    private List<ProductImage> sortedImages(Product product) {
        return product.getImages().stream()
                .sorted(Comparator
                        .comparing(ProductImage::getMainImage).reversed()
                        .thenComparing(ProductImage::getDisplayOrder))
                .toList();
    }

    private ProductVariantResponse toVariantResponse(ProductVariant variant) {
        return new ProductVariantResponse(
                variant.getId(),
                variant.getSku(),
                variant.getSize(),
                variant.getColor(),
                variant.getAvailableQuantity(),
                variant.getEffectivePrice(),
                variant.getActive()
        );
    }
}
