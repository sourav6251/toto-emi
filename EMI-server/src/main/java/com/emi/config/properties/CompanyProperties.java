package com.emi.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "company")
public class CompanyProperties {

    private String name;
    private String shortName;
    private String upi;
    private String address;
    private String phone;
    private String email;
    private String gstin;
    private Defaults defaults = new Defaults();

    @Data
    public static class Defaults {
        private BigDecimal rate = BigDecimal.valueOf(15);
        private String interestType = "FLAT";
        private int tenure = 24;
        private int dueDay = 5;
        private int graceDays = 3;
        private BigDecimal lateFee = BigDecimal.valueOf(150);
        private BigDecimal processingFeePct = new BigDecimal("1.5");
        private BigDecimal minDownPaymentPct = BigDecimal.valueOf(15);
    }
}
