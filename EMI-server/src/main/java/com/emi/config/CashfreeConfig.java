package com.emi.config;

import com.cashfree.pg.Cashfree;
import com.emi.config.properties.CashfreeProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AllArgsConstructor
@Slf4j
public class CashfreeConfig {

    private final CashfreeProperties properties;

    @Bean
    public Cashfree cashfree() {
        String appId = properties.getEffectiveAppId();
        if (appId == null || appId.isBlank()) {
            appId = "placeholder_app_id";
        }

        String secretKey = properties.getEffectiveSecretKey();
        if (secretKey == null || secretKey.isBlank()) {
            secretKey = "placeholder_secret";
        }

        String apiVersion = (properties.getApiVersion() != null && !properties.getApiVersion().isBlank())
                ? properties.getApiVersion().trim()
                : "2023-08-01";

        Cashfree.CFEnvironment env = properties.isProduction()
                ? Cashfree.CFEnvironment.PRODUCTION
                : Cashfree.CFEnvironment.SANDBOX;

        String maskedKey = (secretKey.length() > 6)
                ? secretKey.substring(0, 4) + "..." + secretKey.substring(secretKey.length() - 4)
                : "***";

        log.info("Initialized Cashfree PG client in [{}] mode with API version [{}], App-ID [{}], Secret-Key [{}]",
                env, apiVersion, appId, maskedKey);

        Cashfree cashfree = new Cashfree(env, appId, secretKey, null, null, null);
        cashfree.XApiVersion = apiVersion;
        return cashfree;
    }
}