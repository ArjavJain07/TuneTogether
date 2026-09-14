package com.tunetogether.common;

/** Thrown when a call to an external API (YouTube, Google) fails. Maps to HTTP 502. */
public class UpstreamServiceException extends RuntimeException {

    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public UpstreamServiceException(String message) {
        super(message);
    }
}
