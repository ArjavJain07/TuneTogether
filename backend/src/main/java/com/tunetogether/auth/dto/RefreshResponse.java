package com.tunetogether.auth.dto;

public record RefreshResponse(String accessToken, String refreshToken) {
}
