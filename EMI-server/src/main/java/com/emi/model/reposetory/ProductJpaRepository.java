package com.emi.model.reposetory;

import com.emi.model.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<Product, String> {

    List<Product> findAllByOrderByCreatedAtDesc();

    List<Product> findAllByInStockTrueOrderByCreatedAtDesc();

    @Query("""
            select p from Product p
            where (:productType is null or lower(p.productType) = lower(:productType))
              and (:inStockOnly = false or p.inStock = true)
              and (:search is null or lower(p.productName) like :search or lower(p.brand) like :search or lower(p.model) like :search)
            order by p.createdAt desc
            """)
    Page<Product> searchProducts(@Param("productType") String productType,
                                 @Param("inStockOnly") boolean inStockOnly,
                                 @Param("search") String search,
                                 Pageable pageable);
}

