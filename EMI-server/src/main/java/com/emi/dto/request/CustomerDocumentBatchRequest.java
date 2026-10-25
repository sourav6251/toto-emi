package com.emi.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDocumentBatchRequest {

    @NotBlank(message = "Customer ID is required")
    private String customerId;

    @Valid
    private List<AttachDocumentRequest> documents;

    // Optional direct single-document fields for multipart uploads
    private MultipartFile file;
    private String key;
    private String fileName;
    private String number;

    public String getFileName() {
        return this.fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setFilename(String filename) {
        this.fileName = filename;
    }

    public String getFilename() {
        return this.fileName;
    }
}

