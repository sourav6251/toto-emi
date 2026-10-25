package com.emi.service;

import com.emi.dto.request.CustomerDocumentBatchRequest;
import com.emi.dto.request.UpdateDocumentRequest;
import com.emi.dto.response.CustomerDocumentResponse;
import com.emi.dto.response.DocumentResponse;
import com.emi.model.entity.*;
import com.emi.model.reposetory.*;
import com.emi.utils.enums.CustomerStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentUpdateTest {

    @Mock
    private UploadedFileJpaRepository uploadedFileJpaRepository;
    @Mock
    private FinanceDocumentJpaRepository financeDocumentJpaRepository;
    @Mock
    private FinanceJpaRepository financeJpaRepository;
    @Mock
    private CustomerJpaRepository customerJpaRepository;
    @Mock
    private CustomerDocumentJpaRepository customerDocumentJpaRepository;
    @Mock
    private UserJpaRepository userJpaRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private DocumentService documentService;

    @Test
    void updateDocument_whenTargetIsCustomerBeforeFinanceCreated_updatesCustomerDocument() {
        String customerId = "cust-100";
        Customer customer = Customer.builder()
                .customerId(customerId)
                .fullName("Sunil Das")
                .phoneNumber("9876543210")
                .status(CustomerStatus.ACTIVE)
                .build();

        UploadedFile uploadedFile = UploadedFile.builder()
                .fileId("file-uuid-1")
                .fileNumber("FILE-000001")
                .docKey("aadhaar")
                .url("https://cloudinary.com/test.jpg")
                .build();

        when(uploadedFileJpaRepository.findByFileNumber("FILE-000001")).thenReturn(Optional.of(uploadedFile));
        when(financeJpaRepository.findByFinanceNumber(customerId)).thenReturn(Optional.empty());
        when(financeJpaRepository.findById(customerId)).thenReturn(Optional.empty());
        when(customerJpaRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerDocumentJpaRepository.findByCustomerIdAndDocKey(customerId, "aadhaar")).thenReturn(Optional.empty());
        when(customerDocumentJpaRepository.save(any(CustomerDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                .key("aadhaar")
                .fileId("FILE-000001")
                .number("999988887777")
                .build();

        Object result = documentService.updateDocument("staff-1", true, customerId, request);

        assertNotNull(result);
        assertTrue(result instanceof CustomerDocumentResponse);
        CustomerDocumentResponse response = (CustomerDocumentResponse) result;
        assertEquals(customerId, response.getCustomerId());
        assertEquals("aadhaar", response.getDocKey());
        assertEquals("999988887777", response.getDocNumber());
        verify(customerDocumentJpaRepository).save(any(CustomerDocument.class));
        verify(auditService).log(eq("staff-1"), eq("Document updated"), anyString(), eq(customerId));
    }

    @Test
    void updateDocument_whenTargetIsFinanceSettledOrCreated_updatesFinanceDocumentAndCustomerDoc() {
        String financeId = "fin-500";
        String customerId = "cust-100";
        Customer customer = Customer.builder()
                .customerId(customerId)
                .fullName("Sunil Das")
                .phoneNumber("9876543210")
                .build();

        Finance finance = Finance.builder()
                .financeId(financeId)
                .financeNumber("FIN-2026-000500")
                .customerId(customerId)
                .build();

        UploadedFile uploadedFile = UploadedFile.builder()
                .fileId("file-uuid-2")
                .fileNumber("FILE-000002")
                .docKey("pan")
                .url("https://cloudinary.com/pan.jpg")
                .build();

        when(uploadedFileJpaRepository.findByFileNumber("FILE-000002")).thenReturn(Optional.of(uploadedFile));
        when(financeJpaRepository.findByFinanceNumber(financeId)).thenReturn(Optional.of(finance));
        when(customerJpaRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(financeDocumentJpaRepository.findByFinanceIdAndDocKey(financeId, "pan")).thenReturn(Optional.empty());
        when(financeDocumentJpaRepository.findAllByFinanceId(financeId)).thenReturn(List.of());

        UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                .key("pan")
                .fileId("FILE-000002")
                .number("ABCDE1234F")
                .build();

        Object result = documentService.updateDocument("staff-1", true, financeId, request);

        assertNotNull(result);
        assertTrue(result instanceof List);
        verify(financeDocumentJpaRepository).save(any(FinanceDocument.class));
        verify(customerDocumentJpaRepository).save(any(CustomerDocument.class));
        verify(auditService).log(eq("staff-1"), eq("Document attached"), anyString(), eq("FIN-2026-000500"));
    }

    @Test
    void updateDocumentWithFile_uploadsRawFileAndUpdatesDocument() {
        byte[] validPng = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
                (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
                0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
                0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
                0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
                0x42, 0x60, (byte) 0x82
        };
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", validPng);
        FileStorageService.CloudinaryUploadResult uploadResult = new FileStorageService.CloudinaryUploadResult(
                "public-id-test", "https://cloudinary.com/test.png", "png", validPng.length, 1, 1);
        when(fileStorageService.upload(any(byte[].class), anyString())).thenReturn(uploadResult);
        when(uploadedFileJpaRepository.nextFileNumber()).thenReturn(99L);

        UploadedFile savedUploaded = UploadedFile.builder()
                .fileId("file-uuid-99")
                .fileNumber("FILE-000099")
                .docKey("pan")
                .sizeBytes((long) validPng.length)
                .url("https://cloudinary.com/test.png")
                .build();
        when(uploadedFileJpaRepository.save(any(UploadedFile.class))).thenReturn(savedUploaded);
        when(uploadedFileJpaRepository.findByFileNumber("FILE-000099")).thenReturn(Optional.of(savedUploaded));

        String customerId = "cust-100";
        Customer customer = Customer.builder()
                .customerId(customerId)
                .fullName("Sunil Das")
                .phoneNumber("9876543210")
                .build();
        when(financeJpaRepository.findByFinanceNumber(customerId)).thenReturn(Optional.empty());
        when(financeJpaRepository.findById(customerId)).thenReturn(Optional.empty());
        when(customerJpaRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerDocumentJpaRepository.findByCustomerIdAndDocKey(customerId, "pan")).thenReturn(Optional.empty());
        when(customerDocumentJpaRepository.save(any(CustomerDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        Object result = documentService.updateDocumentWithFile("staff-1", true, customerId, file, "pan", "ABCDE1234F");

        assertNotNull(result);
        assertTrue(result instanceof CustomerDocumentResponse);
        CustomerDocumentResponse res = (CustomerDocumentResponse) result;
        assertEquals("pan", res.getDocKey());
        assertEquals("ABCDE1234F", res.getDocNumber());
        verify(customerDocumentJpaRepository).save(any(CustomerDocument.class));
    }

    @Test
    void attachBatchToCustomer_withCustomFileName_uploadsAndSavesNameToDb() {
        byte[] validPng = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
                (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
                0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
                0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
                0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
                0x42, 0x60, (byte) 0x82
        };
        MockMultipartFile file = new MockMultipartFile("file", "raw.png", "image/png", validPng);
        FileStorageService.CloudinaryUploadResult uploadResult = new FileStorageService.CloudinaryUploadResult(
                "public-id-test3", "https://cloudinary.com/custom.png", "png", validPng.length, 1, 1);
        when(fileStorageService.upload(any(byte[].class), anyString())).thenReturn(uploadResult);
        when(uploadedFileJpaRepository.nextFileNumber()).thenReturn(200L);

        UploadedFile savedUploaded = UploadedFile.builder()
                .fileId("file-uuid-200")
                .fileNumber("FILE-000200")
                .docKey("aadhaar")
                .fileName("aadhaar_front.png")
                .sizeBytes((long) validPng.length)
                .url("https://cloudinary.com/custom.png")
                .build();
        when(uploadedFileJpaRepository.save(any(UploadedFile.class))).thenReturn(savedUploaded);
        when(uploadedFileJpaRepository.findByFileNumber("FILE-000200")).thenReturn(Optional.of(savedUploaded));

        String customerId = "cust-200";
        Customer customer = Customer.builder()
                .customerId(customerId)
                .fullName("Aman Verma")
                .phoneNumber("9876543211")
                .build();
        when(customerJpaRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerDocumentJpaRepository.findByCustomerIdAndDocKey(customerId, "aadhaar")).thenReturn(Optional.empty());
        when(customerDocumentJpaRepository.save(any(CustomerDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerDocumentBatchRequest request = CustomerDocumentBatchRequest.builder()
                .customerId(customerId)
                .key("aadhaar")
                .file(file)
                .fileName("aadhaar_front.png")
                .build();

        List<CustomerDocumentResponse> responses = documentService.attachBatchToCustomer("staff-1", true, request);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("aadhaar_front.png", responses.get(0).getFileName());
        verify(customerDocumentJpaRepository).save(any(CustomerDocument.class));
    }

    @Test
    void attachBatchToCustomer_withPdfFileAndArbitraryKey_success() {
        byte[] pdfBytes = "%PDF-1.4 dummy content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "statement.pdf", "application/pdf", pdfBytes);
        FileStorageService.CloudinaryUploadResult uploadResult = new FileStorageService.CloudinaryUploadResult(
                "public-id-pdf", "https://cloudinary.com/statement.pdf", "pdf", pdfBytes.length, 0, 0);
        when(fileStorageService.upload(any(byte[].class), anyString())).thenReturn(uploadResult);
        when(uploadedFileJpaRepository.nextFileNumber()).thenReturn(201L);

        UploadedFile savedUploaded = UploadedFile.builder()
                .fileId("file-uuid-201")
                .fileNumber("FILE-000201")
                .docKey("salary_slip")
                .fileName("statement.pdf")
                .sizeBytes((long) pdfBytes.length)
                .url("https://cloudinary.com/statement.pdf")
                .build();
        when(uploadedFileJpaRepository.save(any(UploadedFile.class))).thenReturn(savedUploaded);
        when(uploadedFileJpaRepository.findByFileNumber("FILE-000201")).thenReturn(Optional.of(savedUploaded));

        String customerId = "cust-201";
        Customer customer = Customer.builder()
                .customerId(customerId)
                .fullName("Aman Verma")
                .phoneNumber("9876543211")
                .build();
        when(customerJpaRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerDocumentJpaRepository.findByCustomerIdAndDocKey(customerId, "salary_slip")).thenReturn(Optional.empty());
        when(customerDocumentJpaRepository.save(any(CustomerDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerDocumentBatchRequest request = CustomerDocumentBatchRequest.builder()
                .customerId(customerId)
                .key("salary_slip")
                .file(file)
                .fileName("statement.pdf")
                .build();

        List<CustomerDocumentResponse> responses = documentService.attachBatchToCustomer("staff-1", true, request);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("salary_slip", responses.get(0).getDocKey());
        assertEquals("statement.pdf", responses.get(0).getFileName());
        verify(customerDocumentJpaRepository).save(any(CustomerDocument.class));
    }
}

