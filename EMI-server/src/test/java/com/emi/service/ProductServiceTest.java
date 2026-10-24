package com.emi.service;

import com.emi.dto.request.ProductRequest;
import com.emi.dto.response.ProductResponse;
import com.emi.model.entity.Product;
import com.emi.model.reposetory.ProductJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductJpaRepository productJpaRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ProductService productService;

    @Test
    void createProductWithMultipartFiles() {
        MockMultipartFile file1 = new MockMultipartFile("images", "toto1.jpg", "image/jpeg", new byte[]{1, 2, 3});
        MockMultipartFile file2 = new MockMultipartFile("images", "toto2.jpg", "image/jpeg", new byte[]{4, 5, 6});

        when(fileStorageService.upload(eq(file1), eq("products")))
                .thenReturn(new FileStorageService.CloudinaryUploadResult("pid1", "https://res.cloudinary.com/toto1.jpg", "jpg", 3, 100, 100));
        when(fileStorageService.upload(eq(file2), eq("products")))
                .thenReturn(new FileStorageService.CloudinaryUploadResult("pid2", "https://res.cloudinary.com/toto2.jpg", "jpg", 3, 100, 100));

        when(productJpaRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setProductId("PROD-12345");
            return p;
        });

        ProductRequest request = ProductRequest.builder()
                .productName("Mayuri Toto Deluxe")
                .price(new BigDecimal("125000.00"))
                .details("Electric Rickshaw with charger")
                .productType("Toto")
                .brand("Mayuri")
                .model("Deluxe")
                .stockQuantity(10)
                .inStock(true)
                .images(List.of(file1, file2))
                .build();

        ProductResponse response = productService.createProduct("ADMIN-01", request);

        assertNotNull(response);
        assertEquals("PROD-12345", response.getProductId());
        assertEquals("Mayuri Toto Deluxe", response.getProductName());
        assertEquals(2, response.getImages().size());
        assertEquals("https://res.cloudinary.com/toto1.jpg", response.getImages().get(0));
        assertEquals("https://res.cloudinary.com/toto2.jpg", response.getImages().get(1));

        verify(fileStorageService, times(2)).upload(any(MultipartFile.class), eq("products"));
        verify(productJpaRepository, times(1)).save(any(Product.class));
    }
}

