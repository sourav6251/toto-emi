package com.emi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderResponse {
    private String orderId;
    private String financeId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String paymentSessionId;
    private OffsetDateTime expiresAt;
}
