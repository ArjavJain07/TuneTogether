package com.tunetogether.auth.dto;

/** {@code refreshToken} is null for guest sessions - guests have no persisted account to refresh. */
public record AuthResponse(String accessToken, String refreshToken, UserDto user) {
}
