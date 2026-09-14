package com.tunetogether.auth;

import java.security.Principal;
import java.util.List;

/**
 * The authenticated identity attached both to Spring Security's context for REST
 * calls and to a STOMP session's Principal for WebSocket messages, so the room
 * engine and the REST layer agree on "who is this" without duplicating auth logic.
 *
 * {@code appUserId} is null for guests (guests never get an {@code AppUser} row).
 */
public record TuneTogetherPrincipal(
        String subjectId,
        Long appUserId,
        String displayName,
        boolean guest,
        List<String> roles
) implements Principal {

    @Override
    public String getName() {
        return subjectId;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
