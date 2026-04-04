package com.femstep.store.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "product_images")
public class ProductImage extends BaseEntity {

    @Column(nullable = false)
    private String url;

    @Column(name = "alt_text", nullable = false)
    private String altText;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "main_image", nullable = false)
    private Boolean mainImage = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Getters and Setters
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Boolean getMainImage() {
        return mainImage;
    }

    public void setMainImage(Boolean mainImage) {
        this.mainImage = mainImage;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }
}
