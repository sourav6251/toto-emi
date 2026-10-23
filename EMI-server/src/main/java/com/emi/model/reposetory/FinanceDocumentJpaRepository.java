package com.emi.model.reposetory;

import com.emi.model.entity.FinanceDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinanceDocumentJpaRepository extends JpaRepository<FinanceDocument, String> {

    List<FinanceDocument> findAllByFinanceId(String financeId);

    Optional<FinanceDocument> findByFinanceIdAndDocKey(String financeId, String docKey);

    boolean existsByFileId(String fileId);
}
