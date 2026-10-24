package com.emi.model.reposetory;

import com.emi.model.entity.UploadedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UploadedFileJpaRepository extends JpaRepository<UploadedFile, String> {

    Optional<UploadedFile> findByFileNumber(String fileNumber);

    @Query(value = "SELECT nextval('file_number_seq')", nativeQuery = true)
    long nextFileNumber();
}
