package com.emi.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSingleObjectNotDoubleNested() throws Exception {
        Map<String, Object> customer = Map.of("customerId", "CUST-001", "name", "Ramesh");

        ApiResponse response = ApiResponse.success().add("data", customer);
        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("success", root.get("message").asText());
        assertNotNull(root.get("data"));
        // Assert "data" has "customerId" directly, NOT "data.data.customerId"
        assertFalse(root.get("data").has("data"));
        assertEquals("CUST-001", root.get("data").get("customerId").asText());
        assertEquals("Ramesh", root.get("data").get("name").asText());
    }

    @Test
    void testMultiKeyMaintainsMap() throws Exception {
        ApiResponse response = ApiResponse.success()
                .add("items", "list")
                .add("total", 5);
        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("success", root.get("message").asText());
        assertEquals("list", root.get("data").get("items").asText());
        assertEquals(5, root.get("data").get("total").asInt());
    }

    @Test
    void testCustomerDetailResponseOmitsCustomerAndLogin() throws Exception {
        CustomerDetailResponse.CustomerInfo customerInfo = CustomerDetailResponse.CustomerInfo.builder()
                .customerId("CUST-100")
                .fullName("John Doe")
                .userId("USER-100")
                .build();

        CustomerDetailResponse.LoginInfo loginInfo = CustomerDetailResponse.LoginInfo.builder()
                .userId("USER-100")
                .fullName("John Doe")
                .build();

        CustomerDetailResponse detail = new CustomerDetailResponse(customerInfo, loginInfo, java.util.List.of());
        String json = objectMapper.writeValueAsString(detail);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("CUST-100", root.get("customerId").asText());
        assertEquals("USER-100", root.get("userId").asText());
        assertEquals("John Doe", root.get("fullName").asText());
        assertFalse(root.has("customer"), "customer object should be omitted as redundant");
        assertFalse(root.has("login"), "login object should be omitted as redundant");
    }
}

