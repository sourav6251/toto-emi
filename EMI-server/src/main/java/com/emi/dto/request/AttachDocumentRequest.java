package com.emi.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    // Any text; the keys in DocumentTypes just get a label and sort order. 30 = doc_key column length.
    @NotBlank(message = "Document type is required")
    @Size(max = 30, message = "Document type cannot exceed 30 characters")
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
