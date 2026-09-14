package com.tunetogether.common;

import java.time.Instant;

public record ApiError(String error, String message, long timestamp) {

    public static ApiError of(String error, String message) {
        return new ApiError(error, message, Instant.now().toEpochMilli());
    }
}
