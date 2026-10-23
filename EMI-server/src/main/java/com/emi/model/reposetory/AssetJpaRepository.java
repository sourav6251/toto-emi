package com.emi.model.reposetory;

import com.emi.model.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetJpaRepository extends JpaRepository<Asset, String> {
}
