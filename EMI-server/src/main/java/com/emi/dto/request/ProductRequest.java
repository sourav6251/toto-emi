package com.emi.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 150, message = "Product name cannot exceed 150 characters")
    private String productName;

    private List<MultipartFile> images;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    private BigDecimal price;

    private String details;

    @Size(max = 50, message = "Product type cannot exceed 50 characters")
    private String productType;

    @Size(max = 100, message = "Brand name is too long")
    private String brand;

    @Size(max = 100, message = "Model name is too long")
    private String model;

    @Min(value = 0, message = "Stock quantity cannot be negative")
    private Integer stockQuantity;

    private Boolean inStock;
}

