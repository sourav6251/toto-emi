package com.emi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EMI Management System API")
                        .version("1.0.0")
                        .description("API documentation for EMI finance management, loan lifecycle, Cashfree PG online payments, and AutoPay recurring mandates.")
                        .contact(new Contact().name("API Support")));
    }

    @Bean
    public OpenApiCustomizer sortEndpointsCustomizer() {
        return openApi -> {
            Paths paths = openApi.getPaths();
            if (paths == null || paths.isEmpty()) {
                return;
            }

            // Explicit controller declaration order for PaymentController endpoints
            List<String> controllerOrder = List.of(
                    "/payments",
                    "/payments/pay",
                    "/payments/verify/{orderId}",
                    "/payments/autopay/setup/{financeId}",
                    "/payments/autopay/cancel/{financeId}",
                    "/payments/{id}",
                    "/payments/{id}/verify"
            );

            Paths orderedPaths = new Paths();

            // 1. Add explicitly ordered payment endpoints in exact sequence
            for (String path : controllerOrder) {
                if (paths.containsKey(path)) {
                    orderedPaths.addPathItem(path, paths.get(path));
                }
            }

            // 2. Add all other paths preserving their registration
            paths.forEach((key, pathItem) -> {
                if (!orderedPaths.containsKey(key)) {
                    orderedPaths.addPathItem(key, pathItem);
                }
            });

            openApi.setPaths(orderedPaths);
        };
    }
}


