package com.emi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private String productId;
    private String productName;
    private List<String> images;
    private BigDecimal price;
    private String details;
    private String productType;
    private String brand;
    private String model;
    private Integer stockQuantity;
    private boolean inStock;
    private String createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

