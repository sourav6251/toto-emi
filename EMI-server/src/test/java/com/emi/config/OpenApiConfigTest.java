package com.emi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OpenApiConfigTest {

    @Test
    void testEndpointsOrderMatchesControllerDeclaration() {
        OpenApiConfig config = new OpenApiConfig();
        OpenApiCustomizer customizer = config.sortEndpointsCustomizer();

        OpenAPI openAPI = new OpenAPI();
        Paths paths = new Paths();

        // Simulate random JVM / Spring mapping registration order
        paths.addPathItem("/payments/verify/{orderId}", new PathItem());
        paths.addPathItem("/payments/{id}/verify", new PathItem());
        paths.addPathItem("/products", new PathItem());
        paths.addPathItem("/payments", new PathItem());
        paths.addPathItem("/payments/autopay/cancel/{financeId}", new PathItem());
        paths.addPathItem("/payments/pay", new PathItem());
        paths.addPathItem("/payments/{id}", new PathItem());
        paths.addPathItem("/payments/autopay/setup/{financeId}", new PathItem());

        openAPI.setPaths(paths);

        // Apply customizer
        customizer.customise(openAPI);

        List<String> orderedKeys = new ArrayList<>(openAPI.getPaths().keySet());

        assertEquals("/payments", orderedKeys.get(0));
        assertEquals("/payments/pay", orderedKeys.get(1));
        assertEquals("/payments/verify/{orderId}", orderedKeys.get(2));
        assertEquals("/payments/autopay/setup/{financeId}", orderedKeys.get(3));
        assertEquals("/payments/autopay/cancel/{financeId}", orderedKeys.get(4));
        assertEquals("/payments/{id}", orderedKeys.get(5));
        assertEquals("/payments/{id}/verify", orderedKeys.get(6));
        assertEquals("/products", orderedKeys.get(7));
    }
}

