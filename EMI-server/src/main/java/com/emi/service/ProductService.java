package com.emi.service;

import com.emi.dto.request.ProductRequest;
import com.emi.dto.response.ProductResponse;
import com.emi.exception.DomainException;
import com.emi.model.entity.Product;
import com.emi.model.reposetory.ProductJpaRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class ProductService {

    private final ProductJpaRepository productJpaRepository;
    private final AuditService auditService;
    private final FileStorageService fileStorageService;

    @Transactional
    public ProductResponse createProduct(String staffId, ProductRequest request) {
        List<String> imageUrls = uploadImages(request.getImages());

        Product product = Product.builder()
                .productName(request.getProductName().trim())
                .images(imageUrls)
                .price(request.getPrice())
                .details(request.getDetails())
                .productType(request.getProductType() != null && !request.getProductType().isBlank() ? request.getProductType().trim() : null)
                .brand(request.getBrand() != null ? request.getBrand().trim() : null)
                .model(request.getModel() != null ? request.getModel().trim() : null)
                .stockQuantity(request.getStockQuantity() != null ? request.getStockQuantity() : 1)
                .inStock(request.getInStock() != null ? request.getInStock() : true)
                .createdBy(staffId)
                .build();

        product = productJpaRepository.save(product);

        auditService.log(staffId, "Product added to stock",
                product.getProductName() + " — ₹" + product.getPrice().stripTrailingZeros().toPlainString(),
                product.getProductId());

        return toResponse(product);
    }

    @Transactional
    public ProductResponse updateProduct(String staffId, String productId, ProductRequest request) {
        Product product = findProduct(productId);

        if (request.getProductName() != null && !request.getProductName().isBlank()) {
            product.setProductName(request.getProductName().trim());
        }
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            List<String> newUrls = uploadImages(request.getImages());
            if (!newUrls.isEmpty()) {
                product.getImages().addAll(newUrls);
            }
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getDetails() != null) {
            product.setDetails(request.getDetails());
        }
        if (request.getProductType() != null) {
            product.setProductType(request.getProductType().isBlank() ? null : request.getProductType().trim());
        }
        if (request.getBrand() != null) {
            product.setBrand(request.getBrand().trim());
        }
        if (request.getModel() != null) {
            product.setModel(request.getModel().trim());
        }
        if (request.getStockQuantity() != null) {
            product.setStockQuantity(request.getStockQuantity());
            if (request.getStockQuantity() <= 0) {
                product.setInStock(false);
            }
        }
        if (request.getInStock() != null) {
            product.setInStock(request.getInStock());
        }

        product = productJpaRepository.save(product);

        auditService.log(staffId, "Product updated",
                product.getProductName() + " — ₹" + product.getPrice().stripTrailingZeros().toPlainString(),
                product.getProductId());

        return toResponse(product);
    }

    @Transactional
    public void deleteProduct(String staffId, String productId) {
        Product product = findProduct(productId);
        if (product.getImages() != null) {
            for (String url : product.getImages()) {
                try {
                    fileStorageService.delete(url);
                } catch (Exception ignored) {
                }
            }
        }
        productJpaRepository.delete(product);

        auditService.log(staffId, "Product removed from stock",
                product.getProductName(), product.getProductId());
    }

    private List<String> uploadImages(List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    var uploadResult = fileStorageService.upload(file, "products");
                    if (uploadResult != null && uploadResult.secureUrl() != null) {
                        urls.add(uploadResult.secureUrl());
                    }
                }
            }
        }
        return urls;
    }
//
//    @Transactional(readOnly = true)
//    public ProductResponse getProduct(String productId) {
//        return toResponse(findProduct(productId));
//    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listProducts() {
        return productJpaRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private Product findProduct(String productId) {
        return productJpaRepository.findById(productId.trim())
                .orElseThrow(() -> new DomainException("Product not found", HttpStatus.NOT_FOUND));
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .images(new ArrayList<>(product.getImages()))
                .price(product.getPrice())
                .details(product.getDetails())
                .productType(product.getProductType())
                .brand(product.getBrand())
                .model(product.getModel())
                .stockQuantity(product.getStockQuantity())
                .inStock(product.isInStock())
                .createdBy(product.getCreatedBy())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}

