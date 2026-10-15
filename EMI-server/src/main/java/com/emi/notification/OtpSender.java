package com.emi.notification;

/**
 * Delivers an OTP to a phone number (SMS / WhatsApp provider).
 */
public interface OtpSender {

    void send(String phoneNumber, String code);
}
