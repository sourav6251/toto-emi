package com.emi.service;

import com.emi.dto.request.AttachDocumentRequest;
import com.emi.dto.response.DocumentResponse;
import com.emi.dto.response.UploadedFileResponse;
import com.emi.exception.DomainException;
import com.emi.model.entity.Customer;
import com.emi.model.entity.Finance;
import com.emi.model.entity.FinanceDocument;
import com.emi.model.entity.UploadedFile;
import com.emi.model.entity.Users;
import com.emi.model.reposetory.CustomerJpaRepository;
import com.emi.model.reposetory.FinanceDocumentJpaRepository;
import com.emi.model.reposetory.FinanceJpaRepository;
import com.emi.model.reposetory.UploadedFileJpaRepository;
import com.emi.model.reposetory.UserJpaRepository;
import com.emi.utils.DocumentTypes;
import com.emi.utils.enums.DocumentVerification;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class DocumentService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final UploadedFileJpaRepository uploadedFileJpaRepository;
    private final FinanceDocumentJpaRepository financeDocumentJpaRepository;
    private final FinanceJpaRepository financeJpaRepository;
    private final CustomerJpaRepository customerJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final AuditService auditService;
    private final FileStorageService fileStorageService;

    public record StoredFile(Resource resource, String mimeType, String fileName) {
    }

    @Transactional
    public UploadedFileResponse upload(String userId, MultipartFile file, String key) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("A file is required", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        var type = DocumentTypes.find(key)
                .orElseThrow(() -> new DomainException("Unknown document type", HttpStatus.UNPROCESSABLE_CONTENT));
        if (file.getSize() > MAX_BYTES) {
            throw new DomainException("File is larger than 5 MB", HttpStatus.CONTENT_TOO_LARGE);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new DomainException("Could not read the uploaded file", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        ImageInfo image = inspect(bytes);

        // Upload directly to Cloudinary using FileStorageService (no local disk storage)
        FileStorageService.CloudinaryUploadResult cloudResult = fileStorageService.upload(bytes, "emi_documents");
        String cloudinaryUrl = cloudResult.secureUrl();
        String storedName = cloudResult.publicId();

        try {
            long sequence = uploadedFileJpaRepository.nextFileNumber();
            String fileNumber = String.format("FILE-%06d", sequence);
            UploadedFile saved = uploadedFileJpaRepository.save(UploadedFile.builder()
                    .fileNumber(fileNumber)
                    .docKey(type.key())
                    .fileName(clientFileName(file.getOriginalFilename(), image.extension()))
                    .storedName(storedName)
                    .sizeBytes((long) bytes.length)
                    .width(image.width())
                    .height(image.height())
                    .mimeType(image.mimeType())
                    .url(cloudinaryUrl)
                    .uploadedBy(userId)
                    .build());
            return toResponse(saved);
        } catch (RuntimeException e) {
            fileStorageService.delete(cloudinaryUrl);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public StoredFile load(String id) {
        UploadedFile f = findFile(id)
                .orElseThrow(() -> new DomainException("File not found", HttpStatus.NOT_FOUND));
        if (f.getUrl() != null && (f.getUrl().startsWith("http://") || f.getUrl().startsWith("https://"))) {
            try {
                return new StoredFile(new UrlResource(URI.create(f.getUrl())), f.getMimeType(), f.getFileName());
            } catch (MalformedURLException ignored) {
            }
        }
        throw new DomainException("File not found", HttpStatus.NOT_FOUND);
    }

    @Transactional
    public void delete(String userId, String id) {
        UploadedFile file = findFile(id)
                .orElseThrow(() -> new DomainException("File not found", HttpStatus.NOT_FOUND));

        if (financeDocumentJpaRepository.existsByFileId(file.getFileId())) {
            throw new DomainException("Cannot delete file: it is currently attached to a loan agreement",
                    HttpStatus.CONFLICT);
        }

        fileStorageService.delete(file.getUrl());

        uploadedFileJpaRepository.delete(file);

        auditService.log(userId, "Document deleted", file.getFileNumber() + " (" + file.getDocKey() + ")", "");
    }

    @Transactional
    public void attach(String userId, boolean isAdmin, String financeId, AttachDocumentRequest request) {
        Finance finance = financeJpaRepository.findByFinanceNumber(financeId.trim())
                .or(() -> financeJpaRepository.findById(financeId.trim()))
                .orElseThrow(() -> new DomainException("Finance not found", HttpStatus.NOT_FOUND));
        Customer customer = customerJpaRepository.findById(finance.getCustomerId())
                .orElseThrow(() -> new DomainException("Finance not found", HttpStatus.NOT_FOUND));
        if (!isAdmin && customer.getAssignedStaffId() != null && !customer.getAssignedStaffId().equals(userId)) {
            throw new DomainException("Finance not found", HttpStatus.NOT_FOUND);
        }

        var type = DocumentTypes.find(request.getKey())
                .orElseThrow(() -> new DomainException("Unknown document type", HttpStatus.UNPROCESSABLE_CONTENT));
        UploadedFile file = findFile(request.getFileId().trim())
                .orElseThrow(() -> new DomainException("Unknown file", HttpStatus.UNPROCESSABLE_CONTENT));
        if (!file.getDocKey().equals(type.key())) {
            throw new DomainException("This file was uploaded as a different document type",
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }

        String number = request.getNumber() == null || request.getNumber().isBlank() ? null : request.getNumber().trim();
        DocumentVerification verification = isAdmin ? DocumentVerification.VERIFIED : DocumentVerification.PENDING;

        var existing = financeDocumentJpaRepository.findByFinanceIdAndDocKey(finance.getFinanceId(), type.key());
        boolean replaced = existing.isPresent();
        if (replaced && existing.get().getFileId() != null && !existing.get().getFileId().equals(file.getFileId())) {
            String oldFileId = existing.get().getFileId();
            uploadedFileJpaRepository.findById(oldFileId).ifPresent(oldFile -> {
                fileStorageService.delete(oldFile.getUrl());
            });
        }
        FinanceDocument doc = existing.orElseGet(() -> FinanceDocument.builder()
                .financeId(finance.getFinanceId())
                .docKey(type.key())
                .build());
        doc.setFileId(file.getFileId());
        if (number != null) {
            doc.setDocNumber(number);
        }
        doc.setVerification(verification);
        doc.setUploadedBy(userId);
        financeDocumentJpaRepository.save(doc);

        auditService.log(userId, replaced ? "Document replaced" : "Document uploaded",
                type.label() + " on " + finance.getFinanceNumber(), finance.getFinanceNumber());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> forFinance(Finance finance, boolean canView) {
        List<FinanceDocument> docs = financeDocumentJpaRepository.findAllByFinanceId(finance.getFinanceId());
        if (docs.isEmpty()) {
            return List.of();
        }
        Map<String, UploadedFile> files = uploadedFileJpaRepository
                .findAllById(docs.stream().map(FinanceDocument::getFileId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(UploadedFile::getFileId, f -> f));
        Map<String, String> names = new HashMap<>();
        userJpaRepository.findAllById(docs.stream().map(FinanceDocument::getUploadedBy).collect(Collectors.toSet()))
                .forEach((Users u) -> names.put(u.getUserID(), u.getFullName()));

        return docs.stream()
                .sorted((a, b) -> Integer.compare(order(a.getDocKey()), order(b.getDocKey())))
                .map(d -> {
                    UploadedFile f = files.get(d.getFileId());
                    var type = DocumentTypes.find(d.getDocKey());
                    String number = d.getDocNumber() == null ? "" : mask(d.getDocKey(), d.getDocNumber());
                    return new DocumentResponse(
                            d.getDocKey(),
                            type.map(DocumentTypes.DocType::label).orElse(d.getDocKey()),
                            capitalize(d.getVerification().name()),
                            d.getUpdatedAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate(),
                            canView ? number : null,
                            canView && f != null ? f.getFileName() : null,
                            canView && f != null ? (f.getUrl() != null ? f.getUrl() : resolveBaseUrl() + "/documents/" + f.getFileNumber() + "/file") : null,
                            f != null,
                            names.getOrDefault(d.getUploadedBy(), "Unknown"));
                }).toList();
    }

    private String resolveBaseUrl() {
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        } catch (Exception e) {
            return "http://localhost:4000";
        }
    }

    private java.util.Optional<UploadedFile> findFile(String id) {
        return uploadedFileJpaRepository.findByFileNumber(id).or(() -> uploadedFileJpaRepository.findById(id));
    }

    private UploadedFileResponse toResponse(UploadedFile f) {
        String url = f.getUrl() != null && !f.getUrl().isBlank()
                ? f.getUrl()
                : resolveBaseUrl() + "/documents/" + f.getFileNumber() + "/file";
        return new UploadedFileResponse(f.getFileNumber(), f.getDocKey(), url,
                f.getFileName(), f.getSizeBytes(), f.getWidth(), f.getHeight(), f.getMimeType(), f.getCreatedAt(),
                f.getUploadedBy());
    }

    private record ImageInfo(String mimeType, String extension, int width, int height) {
    }

    private ImageInfo inspect(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new DomainException("File must be a JPEG or PNG image", HttpStatus.UNPROCESSABLE_CONTENT);
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase();
                String mime;
                String ext;
                if (Set.of("jpeg", "jpg").contains(format)) {
                    mime = "image/jpeg";
                    ext = ".jpg";
                } else if ("png".equals(format)) {
                    mime = "image/png";
                    ext = ".png";
                } else {
                    throw new DomainException("File must be a JPEG or PNG image", HttpStatus.UNPROCESSABLE_CONTENT);
                }
                reader.setInput(in);
                return new ImageInfo(mime, ext, reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new DomainException("File must be a JPEG or PNG image", HttpStatus.UNPROCESSABLE_CONTENT);
        }
    }

    private String clientFileName(String original, String fallbackExtension) {
        if (original == null || original.isBlank()) {
            return "document" + fallbackExtension;
        }
        String name = Paths.get(original.replace('\\', '/')).getFileName().toString().trim();
        if (name.isEmpty()) {
            return "document" + fallbackExtension;
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private String mask(String key, String number) {
        if (!DocumentTypes.isIdentity(key)) {
            return number;
        }
        String n = number.trim();
        return n.length() <= 4 ? "XXXX" : "X".repeat(n.length() - 4) + n.substring(n.length() - 4);
    }

    private int order(String key) {
        for (int i = 0; i < DocumentTypes.ALL.size(); i++) {
            if (DocumentTypes.ALL.get(i).key().equals(key)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private String capitalize(String upper) {
        return upper.charAt(0) + upper.substring(1).toLowerCase();
    }
}
