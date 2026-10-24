package com.emi.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cashfree Payment Gateway credentials (Developers → API Keys in the Cashfree dashboard).
 * Supports both client-id/client-secret and app-id/secret-key configurations.
 */
@Data
@Component
@ConfigurationProperties(prefix = "cashfree")
public class CashfreeProperties {

    /**
     * Cashfree App ID / Client ID.
     */
    private String appId;
    private String clientId;

    /**
     * Cashfree Secret Key / Client Secret.
     */
    private String secretKey;
    private String clientSecret;

    /**
     * Environment: "sandbox" or "production", or boolean production flag.
     */
    private String environment = "sandbox";
    private boolean production = false;

    /**
     * API version (default: 2023-08-01).
     */
    private String apiVersion = "2023-08-01";

    /**
     * Order expiry time in minutes (default 30, minimum 15 allowed by Cashfree).
     */
    private int orderExpiryMinutes = 30;

    /**
     * Webhook / IPN notification URL.
     */
    private String notifyUrl;
    private String defaultNotifyUrl;

    /**
     * Customer redirect return URL.
     */
    private String returnUrl;
    private String defaultReturnUrl;

    public String getEffectiveAppId() {
        if (clientId != null && !clientId.isBlank()) {
            return clientId.trim();
        }
        if (appId != null && !appId.isBlank()) {
            return appId.trim();
        }
        return null;
    }

    public String getEffectiveSecretKey() {
        if (clientSecret != null && !clientSecret.isBlank()) {
            return clientSecret.trim();
        }
        if (secretKey != null && !secretKey.isBlank()) {
            return secretKey.trim();
        }
        return null;
    }

    public boolean isProduction() {
        if (production) {
            return true;
        }
        return "production".equalsIgnoreCase(environment) || "prod".equalsIgnoreCase(environment);
    }

    public String getNotifyUrl() {
        if (notifyUrl != null && !notifyUrl.isBlank()) {
            return notifyUrl.trim();
        }
        if (defaultNotifyUrl != null && !defaultNotifyUrl.isBlank()) {
            return defaultNotifyUrl.trim();
        }
        return null;
    }

    public String getReturnUrl() {
        if (returnUrl != null && !returnUrl.isBlank()) {
            return returnUrl.trim();
        }
        if (defaultReturnUrl != null && !defaultReturnUrl.isBlank()) {
            return defaultReturnUrl.trim();
        }
        return null;
    }

    public boolean isConfigured() {
        String effectiveApp = getEffectiveAppId();
        String effectiveSecret = getEffectiveSecretKey();
        return effectiveApp != null && !effectiveApp.isBlank() && !"placeholder_app_id".equalsIgnoreCase(effectiveApp)
                && effectiveSecret != null && !effectiveSecret.isBlank() && !"placeholder_secret".equalsIgnoreCase(effectiveSecret);
    }
}
