package com.tunetogether.auth;

/** Thrown when a bearer/STOMP JWT is missing, malformed, expired, or fails signature check. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
