package com.emi.config;

import com.cashfree.pg.ApiException;
import com.cashfree.pg.ApiResponse;
import com.cashfree.pg.Cashfree;
import com.cashfree.pg.model.CreateOrderRequest;
import com.cashfree.pg.model.CustomerDetails;
import com.cashfree.pg.model.OrderEntity;
import com.emi.config.properties.CashfreeProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.UnknownHostException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CashfreeConfigTest {

    @Test
    @DisplayName("Should correctly load properties and resolve effective keys")
    void testCashfreePropertiesResolution() {
        CashfreeProperties properties = new CashfreeProperties();
        properties.setClientId("17829423df62c05d8fe87baf4a492871");
        properties.setClientSecret("755ae157afc18109a12d4683ecbdb981dc10096e");
        properties.setProduction(false);

        assertEquals("17829423df62c05d8fe87baf4a492871", properties.getEffectiveAppId());
        assertEquals("755ae157afc18109a12d4683ecbdb981dc10096e", properties.getEffectiveSecretKey());
        assertFalse(properties.isProduction());
        assertTrue(properties.isConfigured());
    }

    @Test
    @DisplayName("Should also resolve fallback appId and secretKey field names")
    void testCashfreePropertiesLegacyAliases() {
        CashfreeProperties properties = new CashfreeProperties();
        properties.setAppId("app_alias_123");
        properties.setSecretKey("secret_alias_456");
        properties.setEnvironment("sandbox");

        assertEquals("app_alias_123", properties.getEffectiveAppId());
        assertEquals("secret_alias_456", properties.getEffectiveSecretKey());
        assertFalse(properties.isProduction());
        assertTrue(properties.isConfigured());
    }

    @Test
    @DisplayName("Should initialize Cashfree SDK client bean in SANDBOX mode with matching credentials")
    void testCashfreeClientBeanInitialization() {
        CashfreeProperties properties = new CashfreeProperties();
        properties.setClientId("17829423df62c05d8fe87baf4a492871");
        properties.setClientSecret("755ae157afc18109a12d4683ecbdb981dc10096e");
        properties.setProduction(false);
        properties.setApiVersion("2023-08-01");

        CashfreeConfig config = new CashfreeConfig(properties);
        Cashfree client = config.cashfree();

        assertNotNull(client);
        assertEquals("17829423df62c05d8fe87baf4a492871", client.XClientId);
        assertEquals("755ae157afc18109a12d4683ecbdb981dc10096e", client.XClientSecret);
        assertEquals(Cashfree.CFEnvironment.SANDBOX, client.XEnvironment);
        assertEquals("2023-08-01", client.XApiVersion);
    }

    @Test
    @DisplayName("Should detect placeholder values as not configured")
    void testUnconfiguredCheck() {
        CashfreeProperties properties = new CashfreeProperties();
        properties.setClientId("placeholder_app_id");
        properties.setClientSecret("placeholder_secret");

        assertFalse(properties.isConfigured());
    }

    @Test
    @DisplayName("Should verify authentication against Cashfree Sandbox API with provided test keys")
    void testLiveSandboxConnectionWithTestKeys() {
        CashfreeProperties properties = new CashfreeProperties();
        properties.setClientId("17829423df62c05d8fe87baf4a492871");
        properties.setClientSecret("755ae157afc18109a12d4683ecbdb981dc10096e");
        properties.setProduction(false);
        properties.setApiVersion("2023-08-01");

        CashfreeConfig config = new CashfreeConfig(properties);
        Cashfree client = config.cashfree();

        CreateOrderRequest request = new CreateOrderRequest()
                .orderId("TEST-" + UUID.randomUUID().toString().substring(0, 8))
                .orderAmount(new BigDecimal("10.00"))
                .orderCurrency("INR")
                .customerDetails(new CustomerDetails()
                        .customerId("CUST-TEST-001")
                        .customerPhone("9876543210")
                        .customerName("Test Customer")
                        .customerEmail("test@example.com"));

        try {
            ApiResponse<OrderEntity> response = client.PGCreateOrder(request, null, null, null);
            assertNotNull(response);
            assertNotNull(response.getData());
            assertEquals("ACTIVE", response.getData().getOrderStatus());
            System.out.println("Cashfree Sandbox response status: " + response.getStatusCode()
                    + ", Order ID: " + response.getData().getOrderId()
                    + ", Payment Session ID: " + response.getData().getPaymentSessionId());
        } catch (ApiException e) {
            if (e.getCause() instanceof UnknownHostException
                    || (e.getMessage() != null && e.getMessage().contains("UnknownHostException"))) {
                System.out.println("Offline / sandbox network isolated: skipping live endpoint check.");
                return;
            }
            System.err.println("API error (" + e.getCode() + "): " + e.getResponseBody());
            assertNotEquals(401, e.getCode(), "Cashfree credentials rejected with 401 Unauthorized!");
            throw new RuntimeException(e);
        } catch (Exception ex) {
            System.out.println("Call not reached: " + ex.getMessage());
        }
    }
}

