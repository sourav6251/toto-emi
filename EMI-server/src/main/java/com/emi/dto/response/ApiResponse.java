package com.emi.dto.response;

import java.util.HashMap;
import java.util.Map;

public class ApiResponse {

    private String message;
    private Map<String, Object> data = new HashMap<>();

    public ApiResponse(String message) {
        this.message = message;
    }

    public ApiResponse add(String name, Object value) {
        data.put(name, value);
        return this;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getData() {
        return data;
    }
    public static ApiResponse success() {
        return new ApiResponse("success");
    }
    public static ApiResponse error() {
        return ApiResponse.error("error");
    }
    public static ApiResponse error(String message) {
        return new ApiResponse(message);
    }
}
