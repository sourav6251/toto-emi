package com.emi.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDocumentRequest {

    @NotBlank(message = "Document key is required")
    private String key;

    @NotBlank(message = "File ID is required")
    private String fileId;

    @Size(max = 50, message = "Document number is too long")
    private String number;
}

