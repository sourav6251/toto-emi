package com.emi.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Development stand-in for a real SMS provider: writes the OTP to the log.
 * Disabled in prod so codes never end up in production logs; prod must supply a real OtpSender.
 */
@Slf4j
@Component
// @Profile("!prod")
public class LoggingOtpSender implements OtpSender {

    @Override
    public void send(String phoneNumber, String code) {
        log.warn("[DEV ONLY] OTP for {}: {}", phoneNumber, code);
    }
}
