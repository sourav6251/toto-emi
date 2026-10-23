package com.emi.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * An uploaded document photo. It is uploaded first and attached to a finance afterwards by its file number.
 */
@Entity
@Table(name = "uploaded_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String fileId;

    // Human-readable id shown to clients, e.g. FILE-000042
    @Column(nullable = false, unique = true, updatable = false, length = 20)
    private String fileNumber;

    // Document type this photo was uploaded as, e.g. aadhaar
    @Column(nullable = false, updatable = false, length = 30)
    private String docKey;

    // The client's file name, for display only
    @Column(nullable = false, updatable = false)
    private String fileName;

    // Name on disk; generated, never taken from the client
    @Column(nullable = false, unique = true, updatable = false, length = 100)
    private String storedName;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private Long sizeBytes;

    private Integer width;

    private Integer height;

    @Column(nullable = false, updatable = false, length = 50)
    private String mimeType;

    // Full URL to download / view the file
    @Column(length = 500)
    private String url;

    @Column(nullable = false, updatable = false)
    private String uploadedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
