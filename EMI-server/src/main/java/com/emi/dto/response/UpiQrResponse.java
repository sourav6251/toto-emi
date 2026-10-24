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
public class UpiQrResponse {
    private String orderId;
    private BigDecimal amount;
    private String qrCode;
    private OffsetDateTime expiresAt;
}
