package com.emi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoPaySetupResponse {
    private String subscriptionId;
    private String subscriptionSessionId;
    private String cfSubscriptionId;
    private String status;
    private BigDecimal emiAmount;
    private BigDecimal maxAmount;
    private String firstChargeTime;
}

