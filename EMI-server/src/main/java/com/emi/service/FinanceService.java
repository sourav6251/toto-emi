package com.emi.service;

import com.emi.config.properties.CompanyProperties;
import com.emi.dto.request.FinanceCreateRequest;
import com.emi.dto.response.FinanceDetailResponse;
import com.emi.model.entity.Users;
import org.springframework.security.core.Authentication;
import java.time.temporal.ChronoUnit;
import com.emi.dto.response.CustomerDetailResponse.AssetInfo;
import com.emi.dto.response.CustomerDetailResponse.FinanceSummaryDto;
import com.emi.model.reposetory.UserJpaRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.emi.dto.response.CustomerDetailResponse.FinanceInfo;
import com.emi.dto.response.CustomerDetailResponse.InstallmentInfo;
import com.emi.exception.DomainException;
import com.emi.model.entity.Asset;
import com.emi.model.entity.Customer;
import com.emi.model.entity.CustomerDocument;
import com.emi.model.entity.EmiInstallment;
import com.emi.model.entity.Finance;
import com.emi.model.entity.FinanceDocument;
import com.emi.model.reposetory.AssetJpaRepository;
import com.emi.model.reposetory.CustomerDocumentJpaRepository;
import com.emi.model.reposetory.CustomerJpaRepository;
import com.emi.model.reposetory.EmiInstallmentJpaRepository;
import com.emi.model.reposetory.FinanceDocumentJpaRepository;
import com.emi.model.reposetory.FinanceJpaRepository;
import com.emi.utils.DoNumbers;
import com.emi.utils.EmiCalculator;
import com.emi.utils.enums.CustomerStatus;
import com.emi.utils.enums.FinanceStatus;
import com.emi.utils.enums.InterestType;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class FinanceService {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final CustomerJpaRepository customerJpaRepository;
    private final FinanceJpaRepository financeJpaRepository;
    private final AssetJpaRepository assetJpaRepository;
    private final EmiInstallmentJpaRepository emiInstallmentJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final FinanceSummaryAssembler financeSummaryAssembler;
    private final CompanyProperties companyProperties;
    private final PaymentService paymentService;
    private final DocumentService documentService;
    private final CustomerDocumentJpaRepository customerDocumentJpaRepository;
    private final FinanceDocumentJpaRepository financeDocumentJpaRepository;
    private final AuditService auditService;

    private static final String DO_NUMBER_INVALID =
            "DO number may contain letters, digits, spaces and - / . only, must start with a letter or digit, "
                    + "and be at most 40 characters";

    public record DoNumberCheck(String doNumber, boolean available, String message) {
    }

    @Transactional(readOnly = true)
    public DoNumberCheck checkDoNumber(String doNumber) {
        String cleaned = DoNumbers.clean(doNumber);
        if (cleaned.isEmpty()) {
            return new DoNumberCheck(cleaned, false, "DO number is required");
        }
        if (!DoNumbers.isValid(cleaned)) {
            return new DoNumberCheck(cleaned, false, DO_NUMBER_INVALID);
        }
        if (financeJpaRepository.existsByDoNumberKey(DoNumbers.key(cleaned))) {
            return new DoNumberCheck(cleaned, false, "DO number " + cleaned
                    + " is already used on another finance account. Check the delivery order.");
        }
        return new DoNumberCheck(cleaned, true, null);
    }

    @Transactional(readOnly = true)
    public FinanceDetailResponse getFinanceDetail(String callerId, Authentication authentication, String id) {
        Finance f = financeJpaRepository.findByFinanceNumber(id)
                .or(() -> financeJpaRepository.findById(id))
                .orElseThrow(() -> new DomainException("Finance not found", HttpStatus.NOT_FOUND));
        Customer c = customerJpaRepository.findById(f.getCustomerId())
                .orElseThrow(() -> new DomainException("Finance not found", HttpStatus.NOT_FOUND));

//        boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");
//        boolean isCare = hasRole(authentication, "ROLE_CUSTOMER_CARE");
//        if (isAdmin) {
//        } else if (isCare) {
//            if (c.getAssignedStaffId() != null && !c.getAssignedStaffId().equals(callerId)) {
//                throw new DomainException("Finance not found", HttpStatus.NOT_FOUND);
//            }
//        } else if (c.getUserId() == null || !c.getUserId().equals(callerId)) {
//            throw new DomainException("Finance not found", HttpStatus.NOT_FOUND);
//        }

        Asset asset = assetJpaRepository.findById(f.getAssetId()).orElse(null);
        List<EmiInstallment> installments = emiInstallmentJpaRepository
                .findAllByFinanceIdOrderByInstallmentNo(f.getFinanceId());
        String assignedTo = c.getAssignedStaffId() == null ? "Unassigned"
                : userJpaRepository.findById(c.getAssignedStaffId()).map(Users::getFullName).orElse("Unassigned");

        LocalDate today = LocalDate.now();
        FinanceSummaryDto s = financeSummaryAssembler.assemble(f, c, assignedTo, asset, installments, today).dto();

        int grace = f.getGraceDays() != null ? f.getGraceDays() : 0;
        BigDecimal lateFees = BigDecimal.ZERO;
        List<FinanceDetailResponse.ScheduleRow> schedule = new ArrayList<>();
        for (int n = 0; n < installments.size(); n++) {
            EmiInstallment i = installments.get(n);
            lateFees = lateFees.add(i.getLateFee());
            String status = s.getRibbon().get(n);
            boolean unpaid = !Set.of("paid", "waived", "cancelled").contains(status);
            long late = ChronoUnit.DAYS.between(i.getDueDate(), today);
            boolean paidLate = "paid".equals(status) && i.getPaidAt() != null
                    && i.getPaidAt().isAfter(i.getDueDate().plusDays(grace));
            schedule.add(new FinanceDetailResponse.ScheduleRow(
                    i.getInstallmentNo(),
                    i.getDueDate(),
                    i.getEmiAmount(),
                    i.getPrincipalAmount() ,
                     i.getInterestAmount() ,
                    i.getAmountPaid(),
                    i.getPaidAt(),
                    status,
                    unpaid && late > grace,
                    unpaid ? (int) Math.max(late, 0) : 0,
                    paidLate));
        }

        FinanceDetailResponse.ProductDetail product = asset == null ? null : new FinanceDetailResponse.ProductDetail(
                asset.getProductType() != null ? asset.getProductType() : "Other",
                asset.getProductName(),
                asset.getBrand(),
                asset.getModel(),
                asset.getVehicleNumber(),
//                asset.getChassisNumber(),
//                asset.getEngineNumber(),
                asset.getRegistrationNumber(),
                asset.getPurchasePrice(),
                asset.getDealerName(),
                asset.getDealerContact(),
                asset.getPurchaseDate(),
                null);

        String createdByName = userJpaRepository.findById(f.getCreatedBy()).map(Users::getFullName).orElse(null);
        String aadhaarMasked = c.getAadhaarMasked();

        return FinanceDetailResponse.builder()
                .id(s.getId())
                .financeId(s.getFinanceId())
                .financeNumber(s.getFinanceNumber())
                .customerId(s.getCustomerId())
                .customerName(s.getCustomerName())
                .mobile(s.getMobile())
                .assignedToName(s.getAssignedToName())
                .doNumber(s.getDoNumber())
                .principal(s.getPrincipal())
                .emiAmount(s.getEmiAmount())
                .tenure(s.getTenure())
                .createdAt(s.getCreatedAt())
                .status(s.getStatus())
                .worst(s.getWorst())
                .paidTotal(s.getPaidTotal())
                .paidCount(s.getPaidCount())
                .outstanding(s.getOutstanding())
                .overdueAmount(s.getOverdueAmount())
                .overdueCount(s.getOverdueCount())
                .dpd(s.getDpd())
                .nextDue(s.getNextDue())
                .nextDueDate(s.getNextDueDate())
                .ribbon(s.getRibbon())
                .product(product)
                .price(f.getProductPrice())
                .downPayment(f.getDownPayment())
                .totalPayable(f.getTotalPayable())
                .rate(f.getInterestRate())
                .interestType(f.getInterestType())
                .totalInterest(f.getTotalInterest())
                .processingFee(f.getProcessingFee() )
                .otherCharges(f.getOtherCharges() )
                .firstEmiDate(f.getFirstEmiDate())
                .dueDay(f.getEmiDueDay())
                .graceDays(f.getGraceDays())
                .lateFee(f.getLateFeeAmount())
                .lateFees(lateFees)
                .overduePercentage(f.getOverduePercentage())
                .cancelReason(f.getStatus() == FinanceStatus.CANCELLED ? f.getRemarks() : null)
                .createdByName(createdByName)
                .customer(new FinanceDetailResponse.CustomerBrief(c.getCustomerId(), c.getFullName(),
                        c.getPhoneNumber(), c.getAlternatePhoneNumber(), c.getVillage(), c.getDistrict(), aadhaarMasked))
                .schedule(schedule)
                .payments(paymentService.forFinance(f, c, false))
                .documents(documentService.forFinance(f, true))
                .canUploadDocs(true)
                .followups(List.of())
                .upi(companyProperties.getUpi())
                .companyName(companyProperties.getName())
                .build();
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }

    public record FinanceList(List<FinanceSummaryDto> items, int total) {
    }

    private static final Set<String> LIST_STATUSES = Set.of("active", "overdue", "closed", "cancelled");

    @Transactional(readOnly = true)
    public FinanceList listFinances(String status, LocalDate startDate, LocalDate endDate) {
        String wantedStatus = status == null || status.isBlank() ? null : status.trim().toLowerCase();
        if (wantedStatus != null && !LIST_STATUSES.contains(wantedStatus)) {
            throw new DomainException("status must be one of active, overdue, closed, cancelled");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new DomainException("startDate must not be after endDate");
        }
        OffsetDateTime start = startDate == null ? null : startDate.atStartOfDay(INDIA).toOffsetDateTime();
        OffsetDateTime end = endDate == null ? null : endDate.plusDays(1).atStartOfDay(INDIA).toOffsetDateTime();

        List<Object[]> rows = financeJpaRepository.search(start, end);
        if (rows.isEmpty()) {
            return new FinanceList(List.of(), 0);
        }

        Map<String, List<EmiInstallment>> installmentsByFinance = emiInstallmentJpaRepository
                .findAllByFinanceIdIn(rows.stream().map(r -> ((Finance) r[0]).getFinanceId()).toList())
                .stream()
                .sorted(Comparator.comparing(EmiInstallment::getInstallmentNo))
                .collect(Collectors.groupingBy(EmiInstallment::getFinanceId));

        Map<String, String> staffNames = new HashMap<>();
        userJpaRepository.findAllById(rows.stream().map(r -> ((Customer) r[1]).getAssignedStaffId())
                        .filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(u -> staffNames.put(u.getUserID(), u.getFullName()));

        LocalDate today = LocalDate.now();
        List<FinanceSummaryDto> items = new ArrayList<>();
        for (Object[] r : rows) {
            Finance f = (Finance) r[0];
            Customer c = (Customer) r[1];
            Asset a = (Asset) r[2];
            String assignedTo = c.getAssignedStaffId() == null
                    ? "Unassigned" : staffNames.getOrDefault(c.getAssignedStaffId(), "Unassigned");

            var summary = financeSummaryAssembler.assemble(
                    f, c, assignedTo, a, installmentsByFinance.getOrDefault(f.getFinanceId(), List.of()), today);
            FinanceSummaryDto dto = summary.dto();
            boolean matches = wantedStatus == null
                    || ("overdue".equals(wantedStatus)
                            ? dto.getOverdueCount() > 0 && !"cancelled".equals(dto.getStatus())
                            : wantedStatus.equals(dto.getStatus()));
            if (matches) {
                items.add(dto);
            }
        }
        return new FinanceList(items, items.size());
    }

    @Transactional
    public FinanceInfo createFinance(String staffId, FinanceCreateRequest request) {
        Customer customer = customerJpaRepository.findById(request.getCustomerId())
                .or(() -> customerJpaRepository.findByUserId(request.getCustomerId()))
                .orElseThrow(() -> new DomainException("Customer not found", HttpStatus.NOT_FOUND));
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new DomainException("Customer is not active");
        }
//        if (!isAdmin && customer.getAssignedStaffId() != null && !customer.getAssignedStaffId().equals(staffId)) {
//            throw new DomainException("Customer is not assigned to you", HttpStatus.FORBIDDEN);
//        }
        if (request.getDownPayment().compareTo(request.getProductPrice()) >= 0) {
            throw new DomainException("Down payment must be less than the product price");
        }
        String doNumber = DoNumbers.clean(request.getDoNumber());
        if (!DoNumbers.isValid(doNumber)) {
            throw new DomainException(DO_NUMBER_INVALID);
        }
        if (financeJpaRepository.existsByDoNumberKey(DoNumbers.key(doNumber))) {
            throw new DomainException("DO number already exists", HttpStatus.CONFLICT);
        }

        BigDecimal financeAmount = request.getProductPrice().subtract(request.getDownPayment());
        EmiCalculator.Plan plan = EmiCalculator.calculate(
                financeAmount, request.getInterestRate(), request.getTenureMonths());

        FinanceCreateRequest.AssetRequest a = request.getAsset();
        Asset asset = assetJpaRepository.save(Asset.builder()
                .productType(a.getProductType().trim())
                .productName(a.getProductName().trim())
                .brand(a.getBrand())
                .model(a.getModel())
                .vehicleNumber(a.getVehicleNumber())
//                .chassisNumber(a.getChassisNumber())
//                .engineNumber(a.getEngineNumber())
                .registrationNumber(a.getRegistrationNumber())
                .purchasePrice(request.getProductPrice())
                .dealerName(a.getDealerName())
                .dealerContact(a.getDealerContact())
                .purchaseDate(a.getPurchaseDate())
                .build());

        String financeNumber = String.format("FIN-%d-%06d",
                LocalDate.now().getYear(), financeJpaRepository.nextFinanceNumber());

        Finance finance = financeJpaRepository.save(Finance.builder()
                .financeNumber(financeNumber)
                .customerId(customer.getCustomerId())
                .assetId(asset.getAssetId())
                .doNumber(doNumber)
                .productPrice(request.getProductPrice())
                .downPayment(request.getDownPayment())
                .financeAmount(financeAmount)
                .interestType(InterestType.FLAT)
                .interestRate(request.getInterestRate())
                .tenureMonths(request.getTenureMonths())
                .emiAmount(plan.emi())
                .totalInterest(plan.totalInterest())
                .processingFee(orZero(request.getProcessingFee()))
                .otherCharges(orZero(request.getOtherCharges()))
                .totalPayable(plan.totalPayable())
                .firstEmiDate(request.getFirstEmiDate())
                .emiDueDay(request.getFirstEmiDate().getDayOfMonth())
                .graceDays(request.getGraceDays() != null ? request.getGraceDays() : 0)
                .lateFeeAmount(orZero(request.getLateFeeAmount()))
                .overduePercentage(orZero(request.getOverduePercentage()))
                .status(FinanceStatus.PENDING_APPROVAL)
                .remarks(request.getRemarks())
                .createdBy(staffId)
                .build());

        List<CustomerDocument> stagedDocs = customerDocumentJpaRepository.findAllByCustomerId(customer.getCustomerId());
        for (CustomerDocument stagedDoc : stagedDocs) {
            FinanceDocument fd = financeDocumentJpaRepository.findByFinanceIdAndDocKey(finance.getFinanceId(), stagedDoc.getDocKey())
                    .orElseGet(() -> FinanceDocument.builder()
                            .financeId(finance.getFinanceId())
                            .docKey(stagedDoc.getDocKey())
                            .build());
            fd.setCustomerId(customer.getCustomerId());
            fd.setFileId(stagedDoc.getFileId());
            fd.setDocNumber(stagedDoc.getDocNumber());
            fd.setFileName(stagedDoc.getFileName());
            fd.setVerification(stagedDoc.getVerification());
            fd.setUploadedBy(staffId);
            financeDocumentJpaRepository.save(fd);
        }

        List<EmiInstallment> installments = new ArrayList<>();
        for (EmiCalculator.Row row : plan.rows()) {
            installments.add(EmiInstallment.builder()
                    .financeId(finance.getFinanceId())
                    .installmentNo(row.installmentNo())
                    .dueDate(request.getFirstEmiDate().plusMonths(row.installmentNo() - 1L))
                    .principalAmount(row.principal())
                    .interestAmount(row.interest())
                    .emiAmount(row.emi())
                    .build());
        }
        installments = emiInstallmentJpaRepository.saveAll(installments);

        auditService.log(staffId, "Finance created",
                finance.getFinanceNumber() + " — ₹" + financeAmount.stripTrailingZeros().toPlainString()
                        + " for " + (customer.getFullName() != null ? customer.getFullName() : customer.getPhoneNumber()),
                finance.getFinanceNumber());

        return toInfo(finance, asset, installments);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static FinanceInfo toInfo(Finance f, Asset a, List<EmiInstallment> installments) {
        AssetInfo asset = new AssetInfo(a.getAssetId(), a.getProductType(), a.getProductName(), a.getBrand(),
                a.getModel(), a.getVehicleNumber(),
                a.getRegistrationNumber(), a.getPurchasePrice(), a.getDealerName(), a.getDealerContact(),
                a.getPurchaseDate(), a.getCreatedAt(), a.getUpdatedAt());

        List<InstallmentInfo> schedule = installments.stream()
                .map(i -> new InstallmentInfo(i.getInstallmentId(), i.getInstallmentNo(), i.getDueDate(),
                        i.getPrincipalAmount(), i.getInterestAmount(), i.getEmiAmount(), i.getAmountPaid(),
                        i.getLateFee(), i.getLateFeePaid(), i.getStatus(), i.getPaidAt(),
                        i.getCreatedAt(), i.getUpdatedAt()))
                .toList();

        return new FinanceInfo(f.getFinanceId(), f.getFinanceNumber(), f.getDoNumber(), f.getProductPrice(),
                f.getDownPayment(), f.getFinanceAmount(), f.getInterestType(), f.getInterestRate(),
                f.getTenureMonths(), f.getEmiAmount(), f.getTotalInterest(), f.getProcessingFee(),
                f.getOtherCharges(), f.getTotalPayable(), f.getFirstEmiDate(), f.getEmiDueDay(), f.getGraceDays(),
                f.getLateFeeAmount(), f.getStatus(), f.getRemarks(), f.getCreatedBy(), f.getApprovedBy(),
                f.getApprovedAt(), f.getClosedAt(), f.getCreatedAt(), f.getUpdatedAt(), asset, schedule);
    }
}
