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
import com.emi.model.entity.*;
import com.emi.model.reposetory.*;
import com.emi.utils.enums.CustomerStatus;
import com.emi.utils.enums.InterestType;
import com.emi.utils.enums.Role;
import com.emi.utils.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        String customerId = "cust-1001";
        Customer customer = Customer.builder()
                .customerId(customerId)
                .fullName("Rajesh Kumar")
                .phoneNumber("9876543210")
                .status(CustomerStatus.ACTIVE)
                .build();

        when(customerJpaRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(financeJpaRepository.existsByDoNumberKey(anyString())).thenReturn(false);
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

        FinanceCreateRequest request = FinanceCreateRequest.builder()
                .customerId(customerId)
                .productPrice(new BigDecimal("80000"))
                .downPayment(new BigDecimal("20000"))
                .interestRate(new BigDecimal("10.0"))
                .tenureMonths(6)
                .firstEmiDate(LocalDate.now().plusMonths(1).withDayOfMonth(5))
                .doNumber("DO-2026-01")
                .asset(FinanceCreateRequest.AssetRequest.builder()
                        .productType("E-Rickshaw")
                        .productName("Hero Splendor Plus")
                        .build())
                .build();

        FinanceInfo result = financeService.createFinance("staff-1",  request);

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
}
