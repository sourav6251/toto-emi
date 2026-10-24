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
public class PaymentOrderStatusResponse {
    private String orderId;
    private BigDecimal amount;
    private String status;
    private boolean paid;
    private PaymentResponse payment;
}
