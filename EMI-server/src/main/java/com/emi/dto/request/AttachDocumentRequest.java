package com.emi.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachDocumentRequest {

    @NotBlank(message = "Document type is required")
    private String key;

    private MultipartFile file;

    private String fileName;

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
