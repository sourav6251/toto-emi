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
            summary = "List all showroom stock products",
            description = "Allows CUSTOMER, CUSTOMER_CARE, and ADMIN to browse every stock product, newest first."
    )
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE', 'CUSTOMER')")
    public ResponseEntity<?> listProducts() {
        var products = productService.listProducts();
        return ResponseEntity.ok(ApiResponse.success().add("data", products));
    }

//    @Operation(
//            summary = "Get product details by ID",
//            description = "Retrieves full details of a stock product including its images, price, specifications, and availability."
//    )
//    @GetMapping("/{id}")
//    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE', 'CUSTOMER')")
//    public ResponseEntity<?> getProduct(
//            @Parameter(description = "Product UUID")
//            @PathVariable("id") String id) {
//        var product = productService.getProduct(id);
//        return ResponseEntity.ok(ApiResponse.success().add("data", product));
//    }

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
            @ModelAttribute ProductRequest request) {
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

