package com.emi.service;

import com.emi.dto.response.CustomerDetailResponse.FinanceSummaryDto;
import com.emi.dto.response.CustomerDetailResponse.ProductSummaryDto;
import com.emi.model.entity.Asset;
import com.emi.model.entity.Customer;
import com.emi.model.entity.EmiInstallment;
import com.emi.model.entity.Finance;
import com.emi.utils.enums.FinanceStatus;
import com.emi.utils.enums.InstallmentStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class FinanceSummaryAssembler {

    public record Summary(FinanceSummaryDto dto, BigDecimal outstanding, BigDecimal overdueAmount, int worstRank) {
    }

    public Summary assemble(Finance f, Customer customer, String assignedToName, Asset asset,
                            List<EmiInstallment> installments, LocalDate today) {
        String prodType = asset != null && asset.getProductType() != null ? asset.getProductType() : "Other";
        var product = new ProductSummaryDto(
                prodType,
                asset != null ? asset.getBrand() : null,
                asset != null ? asset.getModel() : null,
                asset != null ? asset.getVehicleNumber() : null);

        int grace = f.getGraceDays() != null ? f.getGraceDays() : 0;
        BigDecimal totalPayable = f.getTotalPayable() != null ? f.getTotalPayable() : BigDecimal.ZERO;
        BigDecimal paidTotal = BigDecimal.ZERO;
        int paidCount = 0;
        BigDecimal overdueAmount = BigDecimal.ZERO;
        int overdueCount = 0;
        int dpd = 0;
        List<String> ribbon = new ArrayList<>();
        LocalDate nextDueDate = null;
        BigDecimal nextDue = BigDecimal.ZERO;

        boolean hasOverdue = false;
        boolean hasPartial = false;
        boolean hasDue = false;

        for (EmiInstallment inst : installments) {
            BigDecimal amount = inst.getEmiAmount() != null ? inst.getEmiAmount() : BigDecimal.ZERO;
            BigDecimal paid = inst.getAmountPaid() != null ? inst.getAmountPaid() : BigDecimal.ZERO;
            paidTotal = paidTotal.add(paid);

            LocalDate dueDate = inst.getDueDate();
            long daysDiff = dueDate != null ? ChronoUnit.DAYS.between(dueDate, today) : 0;

            String instStatus;
            if (inst.getStatus() == InstallmentStatus.WAIVED) {
                instStatus = "waived";
            } else if (inst.getStatus() == InstallmentStatus.CANCELLED || f.getStatus() == FinanceStatus.CANCELLED) {
                instStatus = "cancelled";
            } else if (inst.getStatus() == InstallmentStatus.PAID || paid.compareTo(amount) >= 0) {
                instStatus = "paid";
                paidCount++;
            } else {
                boolean pastGrace = daysDiff > grace;
                if (paid.compareTo(BigDecimal.ZERO) > 0) {
                    instStatus = "partial";
                    hasPartial = true;
                    if (pastGrace) {
                        hasOverdue = true;
                        overdueCount++;
                        overdueAmount = overdueAmount.add(amount.subtract(paid));
                        dpd = Math.max(dpd, (int) daysDiff);
                    }
                } else if (pastGrace) {
                    instStatus = "overdue";
                    hasOverdue = true;
                    overdueCount++;
                    overdueAmount = overdueAmount.add(amount.subtract(paid));
                    dpd = Math.max(dpd, (int) daysDiff);
                } else if (daysDiff >= 0) {
                    instStatus = "due";
                    hasDue = true;
                } else {
                    instStatus = "upcoming";
                }

                if (nextDueDate == null && dueDate != null) {
                    nextDueDate = dueDate;
                    nextDue = amount.subtract(paid);
                }
            }
            ribbon.add(instStatus);
        }

        BigDecimal outstanding = f.getStatus() == FinanceStatus.CANCELLED
                ? BigDecimal.ZERO
                : totalPayable.subtract(paidTotal).max(BigDecimal.ZERO);

        String status = f.getStatus() != null ? f.getStatus().name().toLowerCase() : "active";
        if (f.getStatus() != FinanceStatus.CANCELLED) {
            status = outstanding.compareTo(BigDecimal.ZERO) <= 0 ? "closed" : "active";
        }

        String worst;
        int rank;
        if (hasOverdue) {
            worst = "overdue";
            rank = 5;
        } else if (hasPartial) {
            worst = "partial";
            rank = 4;
        } else if (hasDue) {
            worst = "due";
            rank = 3;
        } else if ("closed".equals(status)) {
            worst = "paid";
            rank = 1;
        } else if ("cancelled".equals(status)) {
            worst = "cancelled";
            rank = 0;
        } else {
            worst = "upcoming";
            rank = 2;
        }

        String displayId = f.getFinanceNumber() != null ? f.getFinanceNumber() : f.getFinanceId();
        var dto = new FinanceSummaryDto(
                displayId,
                f.getFinanceId(),
                f.getFinanceNumber(),
                customer.getCustomerId(),
                customer.getFullName(),
                customer.getPhoneNumber(),
                assignedToName,
                product,
                f.getDoNumber(),
                f.getFinanceAmount(),
                f.getEmiAmount(),
                f.getTenureMonths(),
                f.getCreatedAt(),
                status,
                worst,
                paidTotal,
                paidCount,
                outstanding,
                overdueAmount,
                overdueCount,
                dpd,
                nextDue,
                nextDueDate,
                ribbon);
        return new Summary(dto, outstanding, overdueAmount, rank);
    }
}
