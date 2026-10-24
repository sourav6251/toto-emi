package com.emi.service;

import com.emi.model.entity.EmiInstallment;
import com.emi.model.entity.Finance;
import com.emi.utils.enums.InstallmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentOverdueChargeTest {

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                null, null, null, null, null, null, null, null, null, null
        );
    }

    @Test
    @DisplayName("Should return 0 overdue charge when payment is on or before due date")
    void testNoOverdueBeforeDueDate() {
        Finance finance = Finance.builder()
                .graceDays(0)
                .overduePercentage(BigDecimal.valueOf(5.0))
                .build();

        EmiInstallment installment = EmiInstallment.builder()
                .dueDate(LocalDate.of(2026, 5, 5))
                .emiAmount(BigDecimal.valueOf(3000.00))
                .status(InstallmentStatus.PENDING)
                .build();

        LocalDate paymentDate = LocalDate.of(2026, 5, 5);
        BigDecimal overdue = paymentService.calculateOverdueCharge(installment, finance, paymentDate);
        BigDecimal totalDue = paymentService.calculateInstallmentTotalDue(installment, finance, paymentDate);

        assertEquals(BigDecimal.ZERO, overdue);
        assertEquals(0, BigDecimal.valueOf(3000.00).compareTo(totalDue));
    }

    @Test
    @DisplayName("Should return 0 overdue charge when payment is within grace days")
    void testNoOverdueWithinGraceDays() {
        Finance finance = Finance.builder()
                .graceDays(3)
                .overduePercentage(BigDecimal.valueOf(5.0))
                .build();

        EmiInstallment installment = EmiInstallment.builder()
                .dueDate(LocalDate.of(2026, 5, 5))
                .emiAmount(BigDecimal.valueOf(3000.00))
                .status(InstallmentStatus.PENDING)
                .build();

        // 5th + 3 grace days = 8th
        LocalDate paymentDate = LocalDate.of(2026, 5, 8);
        BigDecimal overdue = paymentService.calculateOverdueCharge(installment, finance, paymentDate);

        assertEquals(BigDecimal.ZERO, overdue);
    }

    @Test
    @DisplayName("Should calculate percentage-based overdue charge when payment is late")
    void testOverdueChargeCalculatedAfterDueDate() {
        Finance finance = Finance.builder()
                .graceDays(0)
                .overduePercentage(BigDecimal.valueOf(5.0)) // 5%
                .build();

        EmiInstallment installment = EmiInstallment.builder()
                .dueDate(LocalDate.of(2026, 5, 5))
                .emiAmount(BigDecimal.valueOf(3000.00))
                .status(InstallmentStatus.PENDING)
                .build();

        // Missed 5th, paying on 6th
        LocalDate paymentDate = LocalDate.of(2026, 5, 6);
        BigDecimal overdue = paymentService.calculateOverdueCharge(installment, finance, paymentDate);
        BigDecimal totalDue = paymentService.calculateInstallmentTotalDue(installment, finance, paymentDate);

        // 5% of 3000 = 150.00
        assertEquals(0, new BigDecimal("150.00").compareTo(overdue));
        // Total Due = 3000 + 150 = 3150.00
        assertEquals(0, new BigDecimal("3150.00").compareTo(totalDue));
    }
}

