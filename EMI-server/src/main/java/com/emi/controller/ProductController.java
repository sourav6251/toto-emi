package com.emi.controller;

import com.emi.annotation.CurrentUserId;
import com.emi.dto.request.ProductRequest;
import com.emi.dto.response.ApiResponse;
import com.emi.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Product Stock Catalog", description = "APIs for managing and viewing showroom stock products available for sale or EMI financing")
@RestController
@RequestMapping("/products")
@AllArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(
            summary = "List and search showroom stock products",
            description = "Allows CUSTOMER, CUSTOMER_CARE, and ADMIN to browse stock products. Supports filtering by product type, in-stock status, search keyword, and pagination."
    )
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE', 'CUSTOMER')")
    public ResponseEntity<?> listProducts(
            @Parameter(description = "Optional filter by product type (e.g. Toto, Battery, Charger, Scooter)")
            @RequestParam(value = "productType", required = false) String productType,
            @Parameter(description = "Filter only in-stock items (default true for customers)")
            @RequestParam(value = "inStockOnly", defaultValue = "false") boolean inStockOnly,
            @Parameter(description = "Search keyword matching name, brand, or model")
            @RequestParam(value = "search", required = false) String search,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Number of items per page")
            @RequestParam(value = "size", defaultValue = "20") int size) {

        var result = productService.listProducts(productType, inStockOnly, search, page, size);
        return ResponseEntity.ok(ApiResponse.success()
                .add("products", result.getContent())
                .add("page", result.getNumber())
                .add("size", result.getSize())
                .add("totalElements", result.getTotalElements())
                .add("totalPages", result.getTotalPages()));
    }

    @Operation(
            summary = "Get product details by ID",
            description = "Retrieves full details of a stock product including its images, price, specifications, and availability."
    )
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE', 'CUSTOMER')")
    public ResponseEntity<?> getProduct(
            @Parameter(description = "Product UUID")
            @PathVariable("id") String id) {
        var product = productService.getProduct(id);
        return ResponseEntity.ok(ApiResponse.success().add("data", product));
    }

    @Operation(
            summary = "Add a new product to stock",
            description = "Allows ADMIN or CUSTOMER_CARE staff to list a new product in stock with name, multiple image files (MultipartFile), price, and details. Automatically uploads images to Cloudinary storage."
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> createProduct(
            @Parameter(hidden = true) @CurrentUserId String staffId,
            @Valid @ModelAttribute ProductRequest request) {
        var product = productService.createProduct(staffId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success().add("data", product));
    }

    @Operation(
            summary = "Update an existing stock product",
            description = "Allows ADMIN or CUSTOMER_CARE staff to update product price, details, brand/model, stock quantity, or upload additional image files (MultipartFile)."
    )
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> updateProduct(
            @Parameter(hidden = true) @CurrentUserId String staffId,
            @Parameter(description = "Product UUID")
            @PathVariable("id") String id,
            @Valid @ModelAttribute ProductRequest request) {
        var product = productService.updateProduct(staffId, id, request);
        return ResponseEntity.ok(ApiResponse.success().add("data", product));
    }

    @Operation(
            summary = "Delete / remove a product from stock",
            description = "Allows ADMIN or CUSTOMER_CARE staff to permanently remove a product from stock."
    )
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> deleteProduct(
            @Parameter(hidden = true) @CurrentUserId String staffId,
            @Parameter(description = "Product UUID")
            @PathVariable("id") String id) {
        productService.deleteProduct(staffId, id);
        return ResponseEntity.ok(ApiResponse.success().add("message", "Product removed from stock successfully"));
    }
}

