package com.emi.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.emi.exception.DomainException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class FileStorageService {

    private final Cloudinary cloudinary;

    public record CloudinaryUploadResult(
            String publicId,
            String secureUrl,
            String format,
            long bytes,
            int width,
            int height
    ) {
    }

    /**
     * Uploads raw bytes to Cloudinary under the specified folder.
     */
    public CloudinaryUploadResult upload(byte[] bytes, String folder) {
        if (bytes == null || bytes.length == 0) {
            throw new DomainException("Cannot upload empty file bytes", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "folder", folder != null && !folder.isBlank() ? folder : "emi_documents",
                    "resource_type", "image"
            ));

            String publicId = (String) uploadResult.get("public_id");
            String secureUrl = (String) uploadResult.get("secure_url");
            String format = (String) uploadResult.get("format");
            long size = uploadResult.get("bytes") instanceof Number n ? n.longValue() : bytes.length;
            int width = uploadResult.get("width") instanceof Number n ? n.intValue() : 0;
            int height = uploadResult.get("height") instanceof Number n ? n.intValue() : 0;

            return new CloudinaryUploadResult(publicId, secureUrl, format, size, width, height);
        } catch (IOException e) {
            log.error("Cloudinary upload failed: {}", e.getMessage(), e);
            throw new DomainException("File upload failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Uploads a MultipartFile to Cloudinary.
     */
    public CloudinaryUploadResult upload(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("File is empty or missing", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        try {
            return upload(file.getBytes(), folder);
        } catch (IOException e) {
            log.error("Failed to read MultipartFile bytes: {}", e.getMessage(), e);
            throw new DomainException("Could not read file data", HttpStatus.UNPROCESSABLE_CONTENT);
        }
    }

    /**
     * Deletes a file from Cloudinary by its publicId or by its full URL.
     */
    public boolean delete(String publicIdOrUrl) {
        if (publicIdOrUrl == null || publicIdOrUrl.isBlank()) {
            return false;
        }
        String publicId = extractPublicId(publicIdOrUrl);
        if (publicId == null || publicId.isBlank()) {
            return false;
        }
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("invalidate", true));
            String status = (String) result.get("result");
            log.info("Cloudinary destroy for publicId {}: {}", publicId, status);
            return "ok".equalsIgnoreCase(status);
        } catch (IOException e) {
            log.error("Failed to delete file from Cloudinary: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Extracts public_id from Cloudinary URL if a full URL is provided.
     * E.g.: https://res.cloudinary.com/cloud/image/upload/v1792767255/emi_documents/abc.png -> emi_documents/abc
     */
    public String extractPublicId(String publicIdOrUrl) {
        if (publicIdOrUrl == null) {
            return null;
        }
        String s = publicIdOrUrl.trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            return s; // already a public_id
        }
        int uploadIndex = s.indexOf("/image/upload/");
        if (uploadIndex == -1) {
            uploadIndex = s.indexOf("/upload/");
            if (uploadIndex != -1) {
                s = s.substring(uploadIndex + "/upload/".length());
            }
        } else {
            s = s.substring(uploadIndex + "/image/upload/".length());
        }
        // strip optional version v1234567/
        if (s.matches("^v\\d+/.*")) {
            s = s.substring(s.indexOf('/') + 1);
        }
        // strip extension like .png, .jpg
        int dot = s.lastIndexOf('.');
        if (dot != -1) {
            s = s.substring(0, dot);
        }
        return s;
    }
}

