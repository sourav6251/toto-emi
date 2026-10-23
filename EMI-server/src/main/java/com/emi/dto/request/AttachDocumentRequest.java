package com.emi.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Attaches an uploaded photo to a finance, or replaces the photo of a document it already has.
// number is optional; when omitted the existing number is kept. It travels in the body because
// identity numbers must not appear in URLs.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachDocumentRequest {

    @NotBlank(message = "Document type is required")
    private String key;

    @NotBlank(message = "File is required")
    private String fileId;

    @Size(max = 50, message = "Document number is too long")
    private String number;
}
