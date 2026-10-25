package com.emi.model.reposetory;

import com.emi.model.entity.CustomerDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerDocumentJpaRepository extends JpaRepository<CustomerDocument, String> {

    List<CustomerDocument> findAllByCustomerId(String customerId);

    Optional<CustomerDocument> findByCustomerIdAndDocKey(String customerId, String docKey);

    boolean existsByFileId(String fileId);

    void deleteAllByCustomerId(String customerId);
}

