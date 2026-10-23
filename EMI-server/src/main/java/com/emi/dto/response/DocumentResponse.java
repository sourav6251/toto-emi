package com.emi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// A document on a finance. number, fileName and imageUrl are null unless the caller may view documents
// (admin or care); number is masked for identity documents. hasImage says whether a photo exists at all.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    private String key;
    private String label;
    private String verification;
    private LocalDate uploadedAt;
    private String number;
    private String fileName;
    private String imageUrl;
    private boolean hasImage;
    private String uploadedByName;
}
