package com.emi.controller;


import com.emi.annotation.CurrentUserId;
import com.emi.dto.response.ApiResponse;
import com.emi.service.DocumentService;
import lombok.AllArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@AllArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
public class DocumentController {

    private final DocumentService documentService;

    // Uploads one document photo (JPEG or PNG, up to 5 MB) and returns the FILE-NNNNNN id to attach it with.
    // multipart/form-data with parts `file` and `key` (aadhaar, pan, voter, gold, land, gst, do, bill, asset_other)
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(@CurrentUserId String userId,
                                            @RequestParam(value = "file", required = false) MultipartFile file,
                                            @RequestParam(value = "key") String key) {
        var uploaded = documentService.upload(userId, file, key);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success().add("data", uploaded));
    }

    @GetMapping("/documents/{id}/file")
    public ResponseEntity<?> getDocumentFile(@PathVariable("id") String id) {
        var stored = documentService.load(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(stored.resource());
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<?> deleteDocument(@CurrentUserId String userId, @PathVariable("id") String id) {
        documentService.delete(userId, id);
        return ResponseEntity.ok(ApiResponse.success().add("message", "Document deleted successfully"));
    }

    @GetMapping("/document/pan")
    public ResponseEntity<?> verifyPAN(){
        return null;
    }
    @GetMapping("/document/aadhaar")
    public ResponseEntity<?> verifyAadhaar(){
        return null;
    }
    @GetMapping("/document/ifsc")
    public ResponseEntity<?> verifyIFSC(){
        return null;
    }
    @GetMapping("/document/digilocker")
    public ResponseEntity<?> verifyDigiLocker(){
        return null;
    }
}
