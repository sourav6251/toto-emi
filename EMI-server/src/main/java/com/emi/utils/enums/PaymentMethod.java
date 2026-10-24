package com.emi.utils.enums;

public enum PaymentMethod {
    CASH("Cash"),
    UPI("UPI"),
    BANK_TRANSFER("Bank Transfer"),
    PAYMENT_GATEWAY("Payment Gateway"),
    CHEQUE("Cheque");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    // Everything except cash needs a reference to reconcile against (cheque number for cheques)
    public boolean requiresTxnId() {
        return this != CASH;
    }

    public static PaymentMethod parse(String value) {
        if (value != null) {
            String wanted = value.trim();
            for (PaymentMethod m : values()) {
                if (m.label.equalsIgnoreCase(wanted) || m.name().equalsIgnoreCase(wanted)) {
                    return m;
                }
            }
        }
        return null;
    }
}
