package com.emi.dto.response;

import java.util.LinkedHashMap;
import java.util.Map;

public class ApiResponse {

    private String message;
    private Map<String, Object> data = new LinkedHashMap<>();

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

    public Object getData() {
        // If the payload was added with key "data" as a single object, unwrap it to avoid double nesting ("data": { "data": ... })
        if (data.size() == 1 && data.containsKey("data")) {
            return data.get("data");
        }
        return data;
    }

    public static ApiResponse success() {
        return new ApiResponse("success");
    }

    public static ApiResponse success(Object payload) {
        ApiResponse response = new ApiResponse("success");
        response.add("data", payload);
        return response;
    }

    public static ApiResponse error() {
        return ApiResponse.error("error");
    }

    public static ApiResponse error(String message) {
        return new ApiResponse(message);
    }
}
