package com.foodie.controller;

import com.foodie.service.InvalidBrainTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.DateTimeException;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Central error handling for JSON ({@code @RestController}) endpoints.
 *
 * <p>Every error is returned with a real HTTP status and the body {@code {"error": "..."}}.
 * Standard Spring MVC exceptions (405, 415, ...) keep their default handling via
 * {@link ResponseEntityExceptionHandler}. Unexpected exceptions are logged server-side and
 * reported to the client with a generic message only.
 */
@Slf4j
@RestControllerAdvice(annotations = org.springframework.web.bind.annotation.RestController.class)
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** Recorder token missing, unknown, or regenerated. */
    @ExceptionHandler(InvalidBrainTokenException.class)
    public ResponseEntity<Map<String, String>> handleInvalidBrainToken(InvalidBrainTokenException e) {
        log.warn("Brain request rejected: missing or invalid recorder token");
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /** Request bodies that cannot be parsed, such as an unknown enum value or a badly formatted date. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        log.debug("Unreadable request body: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Malformed request body (check field values and date formats)"));
    }

    @ExceptionHandler({NumberFormatException.class, DateTimeException.class, ClassCastException.class})
    public ResponseEntity<Map<String, String>> handleMalformedInput(Exception e) {
        log.debug("Malformed request value: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Invalid value in request");
    }

    /** Validation errors thrown by services; their messages are written to be user-readable. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage() == null ? "Invalid request" : e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage() == null ? "Not found" : e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return error(HttpStatus.CONFLICT, e.getMessage() == null ? "Request conflicts with current state" : e.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(SecurityException e) {
        log.warn("Access denied: {}", e.getMessage());
        return error(HttpStatus.FORBIDDEN, "Not authorized");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        String reason = e.getReason() != null ? e.getReason() : (status != null ? status.getReasonPhrase() : "Error");
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", reason));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        log.error("Unhandled error in API request: {}", e.getMessage(), e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
