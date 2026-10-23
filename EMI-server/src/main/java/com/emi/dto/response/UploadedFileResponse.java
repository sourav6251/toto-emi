package com.emi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

// id is the FILE-NNNNNN number to pass when attaching the photo to a finance
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadedFileResponse {
    private String id;
    private String key;
    private String url;
    private String fileName;
    private long size;
    private Integer width;
    private Integer height;
    private String mimeType;
    private OffsetDateTime uploadedAt;
    private String uploadedBy;
}
