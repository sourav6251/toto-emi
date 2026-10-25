package com.emi.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class UpdateDocumentFileRequest {

    @NotNull(message = "File is required")
    private MultipartFile file;

    @NotBlank(message = "Document key is required")
    private String key;

    @Size(max = 50, message = "Document number is too long")
    private String number;
}
