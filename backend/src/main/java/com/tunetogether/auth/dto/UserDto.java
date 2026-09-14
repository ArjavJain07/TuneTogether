package com.tunetogether.auth.dto;

/** Matches the shape the original frontend's AUTH.user object already used. */
public record UserDto(String name, String email, String photo, String uid, boolean isGuest) {
}
