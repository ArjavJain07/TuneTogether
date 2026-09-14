package com.tunetogether.common;

import com.tunetogether.auth.InvalidTokenException;
import com.tunetogether.library.PlaylistNotFoundException;
import com.tunetogether.library.TrackAlreadyInPlaylistException;
import com.tunetogether.room.RoomRetiredException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiError> handleInvalidToken(InvalidTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of("unauthorized", e.getMessage()));
    }

    @ExceptionHandler(RoomRetiredException.class)
    public ResponseEntity<ApiError> handleRoomRetired(RoomRetiredException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("room_not_found", e.getMessage()));
    }

    @ExceptionHandler(PlaylistNotFoundException.class)
    public ResponseEntity<ApiError> handlePlaylistNotFound(PlaylistNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("not_found", e.getMessage()));
    }

    @ExceptionHandler(TrackAlreadyInPlaylistException.class)
    public ResponseEntity<ApiError> handleTrackAlreadyInPlaylist(TrackAlreadyInPlaylistException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("already_exists", e.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimit(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(ApiError.of("rate_limited", e.getMessage()));
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ApiError> handleUpstream(UpstreamServiceException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiError.of("upstream_error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.badRequest().body(ApiError.of("validation_error", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiError.of("bad_request", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception e) {
        return ResponseEntity.internalServerError().body(ApiError.of("internal_error", "Something went wrong"));
    }
}
