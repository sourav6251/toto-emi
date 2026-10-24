package com.emi.scheduler;

import com.emi.service.PaymentService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@AllArgsConstructor
public class AutoPayScheduler {

    private final PaymentService paymentService;

    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Kolkata")
    public void chargeDailyOverdueEmis() {
        log.info("Starting midnight AutoPay overdue charge scheduler...");
        try {
            int processed = paymentService.processOverdueAutoPayCharges(LocalDate.now());
            log.info("Midnight AutoPay overdue scheduler finished. Processed {} loan(s).", processed);
        } catch (Exception e) {
            log.error("Error occurred while processing midnight overdue AutoPay charges", e);
        }
    }
}

