package com.emi.service;

import com.emi.config.properties.CompanyProperties;
import com.emi.dto.request.AttachDocumentRequest;
import com.emi.dto.request.CustomerDocumentBatchRequest;
import com.emi.dto.request.CustomerProfileRequest;
import com.emi.dto.request.FinanceCreateRequest;
import com.emi.dto.response.CustomerDetailResponse.FinanceInfo;
import com.emi.dto.response.CustomerDocumentResponse;
import com.emi.dto.response.CustomerResponse;
import com.emi.dto.response.UploadedFileResponse;
import com.emi.exception.DomainException;
import com.emi.model.entity.*;
import com.emi.model.reposetory.*;
import com.emi.utils.enums.CustomerStatus;
import com.emi.utils.enums.FinanceStatus;
import com.emi.utils.enums.InterestType;
import com.emi.utils.enums.Role;
import com.emi.utils.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FinanceThreeStageTest {

    @Mock
    private CustomerJpaRepository customerJpaRepository;
    @Mock
    private UserJpaRepository userJpaRepository;
    @Mock
    private FinanceJpaRepository financeJpaRepository;
    @Mock
    private AssetJpaRepository assetJpaRepository;
    @Mock
    private EmiInstallmentJpaRepository emiInstallmentJpaRepository;
    @Mock
    private CustomerDocumentJpaRepository customerDocumentJpaRepository;
    @Mock
    private FinanceDocumentJpaRepository financeDocumentJpaRepository;
    @Mock
    private UploadedFileJpaRepository uploadedFileJpaRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private CompanyProperties companyProperties;
    @Mock
    private PaymentService paymentService;
    @Mock
    private DocumentService documentService;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private FinanceService financeService;

    @Test
    void stage3_createsFinance_bindsStagedDocuments_andGeneratesEmiSchedule() {
        FinanceCreateRequest request = stubStage3("cust-1001");

        FinanceInfo result = financeService.createFinance("staff-1", false, request);

        assertNotNull(result);
        assertEquals("fin-3001", result.getFinanceId());
        assertEquals("FIN-2026-000101", result.getFinanceNumber());
        assertEquals(6, result.getInstallments().size());
        verify(assetJpaRepository).save(argThat(a -> "E-Rickshaw".equals(a.getProductType())));
        verify(financeJpaRepository).save(argThat(f -> f.getInterestType() == InterestType.FLAT));
        verify(financeDocumentJpaRepository).save(any(FinanceDocument.class));
        verify(emiInstallmentJpaRepository).saveAll(any());
        verify(auditService).log(eq("staff-1"), eq("Finance created"), anyString(), eq("FIN-2026-000101"));
    }

    @Test
    void stage3_byCustomerCare_withoutDoNumber_waitsForApproval() {
        FinanceCreateRequest request = stubStage3("cust-1001");

        financeService.createFinance("care-1", false, request);

        verify(financeJpaRepository).save(argThat(f -> f.getStatus() == FinanceStatus.PENDING_APPROVAL
                && f.getDoNumber() == null && f.getApprovedBy() == null && f.getApprovedAt() == null));
    }

    @Test
    void stage3_byCustomerCare_withDoNumber_isRefused() {
        when(customerJpaRepository.findById("cust-1001")).thenReturn(Optional.of(
                Customer.builder().customerId("cust-1001").status(CustomerStatus.ACTIVE).build()));
        FinanceCreateRequest request = request("cust-1001");
        request.setDoNumber("DO-2026-01");

        DomainException e = assertThrows(DomainException.class,
                () -> financeService.createFinance("care-1", false, request));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        verify(financeJpaRepository, never()).save(any());
        verify(assetJpaRepository, never()).save(any());
    }

    @Test
    void stage3_byAdmin_withDoNumber_startsActive() {
        FinanceCreateRequest request = stubStage3("cust-1001");
        request.setDoNumber("do-2026-01");
        when(financeJpaRepository.existsByDoNumberKey("DO-2026-01")).thenReturn(false);

        financeService.createFinance("admin-1", true, request);

        verify(financeJpaRepository).save(argThat(f -> f.getStatus() == FinanceStatus.ACTIVE
                && "DO-2026-01".equals(f.getDoNumber())
                && "admin-1".equals(f.getApprovedBy()) && f.getApprovedAt() != null));
    }

    @Test
    void stage3_byAdmin_withoutDoNumber_waitsForApproval() {
        FinanceCreateRequest request = stubStage3("cust-1001");

        financeService.createFinance("admin-1", true, request);

        verify(financeJpaRepository).save(argThat(f -> f.getStatus() == FinanceStatus.PENDING_APPROVAL
                && f.getDoNumber() == null));
    }

    @Test
    void approve_addsDoNumber_andActivatesPendingFinance() {
        Finance pending = Finance.builder()
                .financeId("fin-3001")
                .financeNumber("FIN-2026-000101")
                .assetId("asset-2001")
                .status(FinanceStatus.PENDING_APPROVAL)
                .build();
        when(financeJpaRepository.findByFinanceNumber("FIN-2026-000101")).thenReturn(Optional.of(pending));
        when(financeJpaRepository.existsByDoNumberKey("DO-2026-01")).thenReturn(false);
        when(financeJpaRepository.save(any(Finance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assetJpaRepository.findById("asset-2001"))
                .thenReturn(Optional.of(Asset.builder().assetId("asset-2001").build()));
        when(emiInstallmentJpaRepository.findAllByFinanceIdOrderByInstallmentNo("fin-3001")).thenReturn(List.of());

        // Typed the way staff copy it off the paper: lower case, stray spaces
        FinanceInfo result = financeService.approveFinance("admin-1", "FIN-2026-000101", "  do-2026-01 ");

        assertEquals(FinanceStatus.ACTIVE, result.getStatus());
        assertEquals("DO-2026-01", result.getDoNumber());
        assertEquals("admin-1", result.getApprovedBy());
        assertNotNull(result.getApprovedAt());
        verify(auditService).log(eq("admin-1"), eq("Finance approved"), anyString(), eq("FIN-2026-000101"));
    }

    @Test
    void approve_rejectsDoNumberUsedByAnotherFinance() {
        Finance pending = Finance.builder()
                .financeNumber("FIN-2026-000101")
                .status(FinanceStatus.PENDING_APPROVAL)
                .build();
        when(financeJpaRepository.findByFinanceNumber("FIN-2026-000101")).thenReturn(Optional.of(pending));
        when(financeJpaRepository.existsByDoNumberKey("DO-2026-01")).thenReturn(true);

        DomainException e = assertThrows(DomainException.class,
                () -> financeService.approveFinance("admin-1", "FIN-2026-000101", "DO-2026-01"));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        verify(financeJpaRepository, never()).save(any());
    }

    @Test
    void approve_rejectsFinanceThatIsAlreadyActive() {
        Finance active = Finance.builder()
                .financeNumber("FIN-2026-000101")
                .doNumber("DO-2026-01")
                .status(FinanceStatus.ACTIVE)
                .build();
        when(financeJpaRepository.findByFinanceNumber("FIN-2026-000101")).thenReturn(Optional.of(active));

        DomainException e = assertThrows(DomainException.class,
                () -> financeService.approveFinance("admin-1", "FIN-2026-000101", "DO-2026-02"));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        verify(financeJpaRepository, never()).save(any());
    }

    @Test
    void update_rebuildsScheduleOfPendingFinance() {
        String customerId = "cust-1001";
        Finance pending = Finance.builder()
                .financeId("fin-3001")
                .financeNumber("FIN-2026-000101")
                .customerId(customerId)
                .assetId("asset-2001")
                .tenureMonths(6)
                .status(FinanceStatus.PENDING_APPROVAL)
                .build();
        List<EmiInstallment> oldSchedule = List.of(EmiInstallment.builder()
                .financeId("fin-3001").installmentNo(1).emiAmount(new BigDecimal("10500")).build());

        when(financeJpaRepository.findByFinanceNumber("FIN-2026-000101")).thenReturn(Optional.of(pending));
        when(customerJpaRepository.findById(customerId)).thenReturn(Optional.of(
                Customer.builder().customerId(customerId).status(CustomerStatus.ACTIVE).build()));
        when(emiInstallmentJpaRepository.findAllByFinanceIdOrderByInstallmentNo("fin-3001")).thenReturn(oldSchedule);
        when(assetJpaRepository.findById("asset-2001"))
                .thenReturn(Optional.of(Asset.builder().assetId("asset-2001").build()));
        when(assetJpaRepository.save(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(financeJpaRepository.save(any(Finance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(emiInstallmentJpaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FinanceCreateRequest request = request(customerId);
        request.setTenureMonths(12);

        FinanceInfo result = financeService.updateFinance("care-1", "FIN-2026-000101", request);

        assertEquals(12, result.getInstallments().size());
        assertEquals(FinanceStatus.PENDING_APPROVAL, result.getStatus());
        verify(emiInstallmentJpaRepository).deleteAll(oldSchedule);
        assertNull(result.getDoNumber());
    }

    @Test
    void update_refusesDoNumber() {
        Finance pending = Finance.builder()
                .financeId("fin-3001")
                .financeNumber("FIN-2026-000101")
                .customerId("cust-1001")
                .status(FinanceStatus.PENDING_APPROVAL)
                .build();
        when(financeJpaRepository.findByFinanceNumber("FIN-2026-000101")).thenReturn(Optional.of(pending));
        when(customerJpaRepository.findById("cust-1001")).thenReturn(Optional.of(
                Customer.builder().customerId("cust-1001").status(CustomerStatus.ACTIVE).build()));
        FinanceCreateRequest request = request("cust-1001");
        request.setDoNumber("DO-2026-01");

        assertThrows(DomainException.class,
                () -> financeService.updateFinance("care-1", "FIN-2026-000101", request));

        verify(financeJpaRepository, never()).save(any());
    }

    @Test
    void update_rejectsApprovedFinance() {
        Finance active = Finance.builder()
                .financeId("fin-3001")
                .financeNumber("FIN-2026-000101")
                .status(FinanceStatus.ACTIVE)
                .build();
        when(financeJpaRepository.findByFinanceNumber("FIN-2026-000101")).thenReturn(Optional.of(active));

        DomainException e = assertThrows(DomainException.class,
                () -> financeService.updateFinance("care-1", "FIN-2026-000101", request("cust-1001")));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        verify(emiInstallmentJpaRepository, never()).saveAll(any());
    }

    // Stubs everything stage 3 touches and returns a valid request for that customer.
    private FinanceCreateRequest stubStage3(String customerId) {
        Customer customer = Customer.builder()
                .customerId(customerId)
                .fullName("Rajesh Kumar")
                .phoneNumber("9876543210")
                .status(CustomerStatus.ACTIVE)
                .build();

        when(customerJpaRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(financeJpaRepository.nextFinanceNumber()).thenReturn(101L);

        Asset savedAsset = Asset.builder()
                .assetId("asset-2001")
                .productName("Hero Splendor Plus")
                .purchasePrice(new BigDecimal("80000"))
                .build();
        when(assetJpaRepository.save(any(Asset.class))).thenReturn(savedAsset);

        Finance savedFinance = Finance.builder()
                .financeId("fin-3001")
                .financeNumber("FIN-2026-000101")
                .customerId(customerId)
                .assetId("asset-2001")
                .doNumber("DO-2026-01")
                .productPrice(new BigDecimal("80000"))
                .downPayment(new BigDecimal("20000"))
                .financeAmount(new BigDecimal("60000"))
                .interestRate(new BigDecimal("10.0"))
                .tenureMonths(6)
                .firstEmiDate(LocalDate.now().plusMonths(1).withDayOfMonth(5))
                .build();
        when(financeJpaRepository.save(any(Finance.class))).thenReturn(savedFinance);

        CustomerDocument stagedDoc = CustomerDocument.builder()
                .id("cdoc-1")
                .customerId(customerId)
                .docKey("aadhaar")
                .fileId("file-99")
                .docNumber("123456789012")
                .build();
        when(customerDocumentJpaRepository.findAllByCustomerId(customerId))
                .thenReturn(List.of(stagedDoc));
        when(financeDocumentJpaRepository.findByFinanceIdAndDocKey("fin-3001", "aadhaar"))
                .thenReturn(Optional.empty());

        when(emiInstallmentJpaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        return request(customerId);
    }

    private static FinanceCreateRequest request(String customerId) {
        return FinanceCreateRequest.builder()
                .customerId(customerId)
                .productPrice(new BigDecimal("80000"))
                .downPayment(new BigDecimal("20000"))
                .interestRate(new BigDecimal("10.0"))
                .tenureMonths(6)
                .firstEmiDate(LocalDate.now().plusMonths(1).withDayOfMonth(5))
                .asset(FinanceCreateRequest.AssetRequest.builder()
                        .productType("E-Rickshaw")
                        .productName("Hero Splendor Plus")
                        .build())
                .build();
    }
}
