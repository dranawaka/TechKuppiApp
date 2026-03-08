package com.tech.techkuppiapp.dto;

/**
 * Simple API response wrapper for REST endpoints.
 */
public class ApiResponse {
    private final boolean success;
    private final String message;

    public ApiResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static ApiResponse ok(String message) {
        return new ApiResponse(true, message);
    }

    public static ApiResponse error(String message) {
        return new ApiResponse(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
