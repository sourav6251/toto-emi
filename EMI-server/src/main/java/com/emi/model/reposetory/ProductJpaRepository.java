package com.emi.model.reposetory;

import com.emi.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<Product, String> {

    List<Product> findAllByOrderByCreatedAtDesc();

    List<Product> findAllByInStockTrueOrderByCreatedAtDesc();
}
