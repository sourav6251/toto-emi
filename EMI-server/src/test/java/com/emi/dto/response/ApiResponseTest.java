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
}

