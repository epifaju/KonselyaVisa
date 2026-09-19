package com.konselyavisa.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, String messageKey, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, String messageKey) {
        return new ApiResponse<>(true, data, messageKey, Instant.now());
    }

    public static <T> ApiResponse<T> error(String messageKey) {
        return new ApiResponse<>(false, null, messageKey, Instant.now());
    }
}
