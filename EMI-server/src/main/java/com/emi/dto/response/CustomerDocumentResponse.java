package com.emi.dto.response;

import com.emi.utils.enums.DocumentVerification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDocumentResponse {

    private String id;
    private String customerId;
    private String docKey;
    private String fileId;
    private String docNumber;
    private String fileName;
    private String url;
    private DocumentVerification verification;
    private OffsetDateTime uploadedAt;
}

