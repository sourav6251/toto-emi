package com.emi.utils.enums;

// A staff collection is PENDING until an admin verifies it; only VERIFIED payments are applied to the EMI schedule
public enum PaymentStatus {
    PENDING, VERIFIED, REJECTED
}
