package com.emi.service;

import com.emi.dto.request.AttachDocumentRequest;
import com.emi.dto.request.CustomerDocumentBatchRequest;
import com.emi.dto.request.UpdateDocumentRequest;
import com.emi.dto.response.CustomerDocumentResponse;
import com.emi.dto.response.DocumentResponse;
import com.emi.dto.response.UploadedFileResponse;
import com.emi.exception.DomainException;
import com.emi.model.entity.Customer;
import com.emi.model.entity.CustomerDocument;
import com.emi.model.entity.Finance;
import com.emi.model.entity.FinanceDocument;
import com.emi.model.entity.UploadedFile;
import com.emi.model.entity.Users;
import com.emi.model.reposetory.CustomerDocumentJpaRepository;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private final CustomerDocumentJpaRepository customerDocumentJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final AuditService auditService;
    private final FileStorageService fileStorageService;

    public record StoredFile(Resource resource, String mimeType, String fileName) {
    }

    @Transactional
    public UploadedFileResponse upload(String userId, MultipartFile file, String key) {
        return upload(userId, file, key, null);
    }

    @Transactional
    public UploadedFileResponse upload(String userId, MultipartFile file, String key, String customFileName) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("A file is required", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        String docKey = (key != null && !key.isBlank()) ? key.trim().toLowerCase() : "document";
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
        deleteOnRollback(cloudinaryUrl);

        String rawName = (customFileName != null && !customFileName.isBlank()) ? customFileName.trim() : file.getOriginalFilename();
        String storedFileName = clientFileName(rawName, image.extension());

        long sequence = uploadedFileJpaRepository.nextFileNumber();
        String fileNumber = String.format("FILE-%06d", sequence);
        UploadedFile saved = uploadedFileJpaRepository.save(UploadedFile.builder()
                .fileNumber(fileNumber)
                .docKey(docKey)
                .fileName(storedFileName)
                .storedName(storedName)
                .sizeBytes((long) bytes.length)
                .width(image.width())
                .height(image.height())
                .mimeType(image.mimeType())
                .url(cloudinaryUrl)
                .uploadedBy(userId)
                .build());
        return toResponse(saved);
    }

    // A file already sent to storage is removed again if the transaction recording it rolls back
    private void deleteOnRollback(String url) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    fileStorageService.delete(url);
                }
            }
        });
    }

    // A replaced file is removed from storage only after the new row commits, and only if no
    // customer or finance document still points at it (Stage 3 shares files with the finance)
    private void deleteWhenUnused(String fileId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            String url = unusedFileUrl(fileId);
            if (url != null) {
                fileStorageService.delete(url);
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            private String url;

            @Override
            public void beforeCommit(boolean readOnly) {
                url = unusedFileUrl(fileId);
            }

            @Override
            public void afterCommit() {
                if (url != null) {
                    fileStorageService.delete(url);
                }
            }
        });
    }

    private String unusedFileUrl(String fileId) {
        if (customerDocumentJpaRepository.existsByFileId(fileId) || financeDocumentJpaRepository.existsByFileId(fileId)) {
            return null;
        }
        return uploadedFileJpaRepository.findById(fileId).map(UploadedFile::getUrl).orElse(null);
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
                    java.util.Optional<DocumentTypes.DocType> type = DocumentTypes.find(d.getDocKey());
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
        if (bytes == null || bytes.length == 0) {
            throw new DomainException("File is empty", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        // PDF magic bytes: %PDF (0x25, 0x50, 0x44, 0x46)
        if (bytes.length >= 4 && bytes[0] == 0x25 && bytes[1] == 0x50 && bytes[2] == 0x44 && bytes[3] == 0x46) {
            return new ImageInfo("application/pdf", ".pdf", 0, 0);
        }

        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new DomainException("File must be a PDF, JPEG, or PNG document", HttpStatus.UNPROCESSABLE_CONTENT);
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
                    throw new DomainException("File must be a PDF, JPEG, or PNG document", HttpStatus.UNPROCESSABLE_CONTENT);
                }
                reader.setInput(in);
                return new ImageInfo(mime, ext, reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new DomainException("File must be a PDF, JPEG, or PNG document", HttpStatus.UNPROCESSABLE_CONTENT);
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
            if (DocumentTypes.ALL.get(i).key().equalsIgnoreCase(key)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private String capitalize(String upper) {
        return upper.charAt(0) + upper.substring(1).toLowerCase();
    }

    @Transactional
    public CustomerDocumentResponse attachToCustomer(String userId, boolean isAdmin, String customerId, String key, String fileId, String number) {
        Customer customer = customerJpaRepository.findById(customerId.trim())
                .or(() -> customerJpaRepository.findByUserId(customerId.trim()))
                .orElseThrow(() -> new DomainException("Customer not found", HttpStatus.NOT_FOUND));

        String docKey = (key != null && !key.isBlank()) ? key.trim().toLowerCase() : "document";
        UploadedFile file = findFile(fileId.trim())
                .orElseThrow(() -> new DomainException("Unknown file", HttpStatus.UNPROCESSABLE_CONTENT));

        // For now every upload is verified instantly, whoever uploads it
        DocumentVerification verification = DocumentVerification.VERIFIED;
        String docNumber = (number != null && !number.isBlank()) ? number.trim() : null;

        CustomerDocument doc = customerDocumentJpaRepository.findByCustomerIdAndDocKey(customer.getCustomerId(), docKey)
                .orElseGet(() -> CustomerDocument.builder()
                        .customerId(customer.getCustomerId())
                        .docKey(docKey)
                        .build());

        if (doc.getFileId() != null && !doc.getFileId().equals(file.getFileId())) {
            deleteWhenUnused(doc.getFileId());
        }

        doc.setFileId(file.getFileId());
        doc.setDocNumber(docNumber);
        doc.setFileName(file.getFileName());
        doc.setUrl(file.getUrl());
        doc.setVerification(verification);
        doc.setUploadedBy(userId);

        CustomerDocument saved = customerDocumentJpaRepository.save(doc);
        return toCustomerDocumentResponse(saved);
    }

    @Transactional
    public List<CustomerDocumentResponse> attachBatchToCustomer(String userId, boolean isAdmin, CustomerDocumentBatchRequest request) {
        Customer customer = customerJpaRepository.findById(request.getCustomerId().trim())
                .or(() -> customerJpaRepository.findByUserId(request.getCustomerId().trim()))
                .orElseThrow(() -> new DomainException("Customer not found", HttpStatus.NOT_FOUND));

        List<AttachDocumentRequest> docs = new java.util.ArrayList<>();
        if (request.getDocuments() != null && !request.getDocuments().isEmpty()) {
            docs.addAll(request.getDocuments());
        } else if (request.getFile() != null && !request.getFile().isEmpty()) {
            docs.add(AttachDocumentRequest.builder()
                    .key(request.getKey())
                    .file(request.getFile())
                    .fileName(request.getFileName())
                    .build());
        }

        if (docs.isEmpty()) {
            throw new DomainException("At least one document file is required", HttpStatus.BAD_REQUEST);
        }

        List<CustomerDocumentResponse> responses = new java.util.ArrayList<>();
        for (AttachDocumentRequest doc : docs) {
            if (doc.getFile() == null || doc.getFile().isEmpty()) {
                throw new DomainException("File is required for " + doc.getKey(), HttpStatus.BAD_REQUEST);
            }
            UploadedFileResponse uploaded = upload(userId, doc.getFile(), doc.getKey(), doc.getFileName());
            CustomerDocumentResponse res = attachToCustomer(userId, isAdmin, customer.getCustomerId(), doc.getKey(), uploaded.getId(), null);
            responses.add(res);
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public List<CustomerDocumentResponse> getCustomerDocuments(String customerId) {
        return customerDocumentJpaRepository.findAllByCustomerId(customerId).stream()
                .map(this::toCustomerDocumentResponse)
                .toList();
    }

    private CustomerDocumentResponse toCustomerDocumentResponse(CustomerDocument doc) {
        return CustomerDocumentResponse.builder()
                .id(doc.getId())
                .customerId(doc.getCustomerId())
                .docKey(doc.getDocKey())
                .fileId(doc.getFileId())
                .docNumber(doc.getDocNumber())
                .fileName(doc.getFileName())
                .url(doc.getUrl())
                .verification(doc.getVerification())
                .uploadedAt(doc.getCreatedAt())
                .build();
    }

    @Transactional
    public Object updateDocumentWithFile(String staffId, boolean isAdmin, String id, MultipartFile file, String key, String number) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("File is required", HttpStatus.BAD_REQUEST);
        }
        UploadedFileResponse uploaded = upload(staffId, file, key);
        UpdateDocumentRequest request = new UpdateDocumentRequest(key, uploaded.getId(), number);
        return updateDocument(staffId, isAdmin, id, request);
    }

    @Transactional
    public Object updateDocument(String staffId, boolean isAdmin, String id, UpdateDocumentRequest request) {
        String trimmedId = id != null ? id.trim() : "";
        String docKey = (request.getKey() != null && !request.getKey().isBlank()) ? request.getKey().trim().toLowerCase() : "document";
        String docLabel = DocumentTypes.find(docKey).map(DocumentTypes.DocType::label).orElse(docKey);
        UploadedFile newFile = findFile(request.getFileId().trim())
                .orElseThrow(() -> new DomainException("Unknown file", HttpStatus.UNPROCESSABLE_CONTENT));

        // For now every upload is verified instantly, whoever uploads it
        DocumentVerification verification = DocumentVerification.VERIFIED;
        String docNumber = (request.getNumber() != null && !request.getNumber().isBlank()) ? request.getNumber().trim() : null;

        // Check if id corresponds to an existing Finance agreement
        java.util.Optional<Finance> financeOpt = financeJpaRepository.findByFinanceNumber(trimmedId)
                .or(() -> financeJpaRepository.findById(trimmedId));

        if (financeOpt.isPresent()) {
            Finance finance = financeOpt.get();
            Customer customer = customerJpaRepository.findById(finance.getCustomerId())
                    .orElseThrow(() -> new DomainException("Customer not found", HttpStatus.NOT_FOUND));
//            if (!isAdmin && customer.getAssignedStaffId() != null && !customer.getAssignedStaffId().equals(staffId)) {
//                throw new DomainException("Finance is not assigned to you", HttpStatus.FORBIDDEN);
//            }

            java.util.Optional<FinanceDocument> existingOpt = financeDocumentJpaRepository.findByFinanceIdAndDocKey(finance.getFinanceId(), docKey);
            boolean replaced = existingOpt.isPresent();
            if (replaced && existingOpt.get().getFileId() != null && !existingOpt.get().getFileId().equals(newFile.getFileId())) {
                deleteWhenUnused(existingOpt.get().getFileId());
            }

            FinanceDocument fd = existingOpt.orElseGet(() -> FinanceDocument.builder()
                    .financeId(finance.getFinanceId())
                    .docKey(docKey)
                    .build());
            fd.setCustomerId(customer.getCustomerId());
            fd.setFileId(newFile.getFileId());
            if (docNumber != null) {
                fd.setDocNumber(docNumber);
            }
            fd.setFileName(newFile.getFileName());
            fd.setVerification(verification);
            fd.setUploadedBy(staffId);
            financeDocumentJpaRepository.save(fd);

            // Also keep customer_documents in sync
            updateCustomerDocumentRow(customer.getCustomerId(), docKey, newFile, docNumber, verification, staffId);

            auditService.log(staffId, replaced ? "Document updated" : "Document attached",
                    docLabel + " on " + finance.getFinanceNumber(), finance.getFinanceNumber());

            return forFinance(finance, true);
        }

        // Check if id corresponds to a Customer (e.g. before finance is created)
        java.util.Optional<Customer> customerOpt = customerJpaRepository.findById(trimmedId)
                .or(() -> customerJpaRepository.findByUserId(trimmedId));

        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
//            if (!isAdmin && customer.getAssignedStaffId() != null && !customer.getAssignedStaffId().equals(staffId)) {
//                throw new DomainException("Customer is not assigned to you", HttpStatus.FORBIDDEN);
//            }

            CustomerDocument savedCd = updateCustomerDocumentRow(customer.getCustomerId(), docKey, newFile, docNumber, verification, staffId);

            auditService.log(staffId, "Document updated",
                    docLabel + " for customer " + (customer.getFullName() != null ? customer.getFullName() : customer.getPhoneNumber()),
                    customer.getCustomerId());

            return toCustomerDocumentResponse(savedCd);
        }

        throw new DomainException("Customer or Finance not found with id: " + id, HttpStatus.NOT_FOUND);
    }

    private CustomerDocument updateCustomerDocumentRow(String customerId, String docKey, UploadedFile file, String docNumber, DocumentVerification verification, String staffId) {
        java.util.Optional<CustomerDocument> existingOpt = customerDocumentJpaRepository.findByCustomerIdAndDocKey(customerId, docKey);
        if (existingOpt.isPresent() && existingOpt.get().getFileId() != null && !existingOpt.get().getFileId().equals(file.getFileId())) {
            deleteWhenUnused(existingOpt.get().getFileId());
        }

        CustomerDocument cd = existingOpt.orElseGet(() -> CustomerDocument.builder()
                .customerId(customerId)
                .docKey(docKey)
                .build());
        cd.setFileId(file.getFileId());
        if (docNumber != null) {
            cd.setDocNumber(docNumber);
        }
        cd.setFileName(file.getFileName());
        cd.setUrl(file.getUrl());
        cd.setVerification(verification);
        cd.setUploadedBy(staffId);
        return customerDocumentJpaRepository.save(cd);
    }
}
