package com.foodie.controller;

import com.foodie.exception.BadRequestException;
import com.foodie.exception.ConflictException;
import com.foodie.exception.ExternalServiceException;
import com.foodie.exception.ForbiddenException;
import com.foodie.exception.InvalidBrainTokenException;
import com.foodie.exception.NotFoundException;
import com.foodie.exception.UnauthenticatedException;
import com.foodie.exception.UnsafeUrlException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.DateTimeException;
import java.util.Map;

/**
 * Central error handling for JSON ({@code @RestController}) endpoints.
 *
 * <p>Every error is returned with a real HTTP status and the body {@code {"error": "..."}},
 * including the standard Spring MVC errors (404, 405, 415, ...). Only the application's own typed
 * exceptions ({@link BadRequestException}, {@link NotFoundException}, {@link ConflictException},
 * ...) have their messages shown to the client. Anything else, including a bare
 * {@code IllegalArgumentException} or {@code IllegalStateException} from a library, is a bug: it is
 * logged with its stack trace and reported with a generic message.
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

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthenticated(UnauthenticatedException e) {
        log.warn("Unauthenticated request: {}", e.getMessage());
        return error(HttpStatus.UNAUTHORIZED, "Please log in.");
    }

    /**
     * Standard Spring MVC errors (404, 405, 415, ...): same status and headers as the defaults,
     * but with the {@code {"error": "..."}} body used everywhere else instead of a ProblemDetail.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        String message;
        if (statusCode.is5xxServerError()) {
            log.error("Server error handling API request: {}", ex.getMessage(), ex);
            message = "Something went wrong. Please try again.";
        } else if (body instanceof ProblemDetail problem && problem.getDetail() != null) {
            message = problem.getDetail();
        } else {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            message = status != null ? status.getReasonPhrase() : "Invalid request";
        }
        return super.handleExceptionInternal(ex, Map.of("error", message), headers, statusCode, request);
    }

    /** {@code @Valid} failures on a JSON body: report the first problem as {@code {"error": "field: message"}}. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            org.springframework.web.bind.MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null
                ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                : "Invalid request";
        return ResponseEntity.badRequest().body(Map.of("error", message));
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

    /** Unparseable numbers/dates in request parameters (their messages are not user-friendly). */
    @ExceptionHandler({NumberFormatException.class, DateTimeException.class})
    public ResponseEntity<Map<String, String>> handleMalformedInput(Exception e) {
        log.debug("Malformed request value: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Invalid value in request");
    }

    /** Validation errors thrown by services; their messages are written to be user-readable. */
    @ExceptionHandler({BadRequestException.class, UnsafeUrlException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage() == null ? "Invalid request" : e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage() == null ? "Not found" : e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ConflictException e) {
        return error(HttpStatus.CONFLICT, e.getMessage() == null ? "Request conflicts with current state" : e.getMessage());
    }

    /** A unique/foreign-key/not-null constraint rejected the write. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, "That conflicts with existing data (for example a duplicate).");
    }

    @ExceptionHandler({ForbiddenException.class, SecurityException.class})
    public ResponseEntity<Map<String, String>> handleForbidden(SecurityException e) {
        log.warn("Access denied: {}", e.getMessage());
        return error(HttpStatus.FORBIDDEN, "Not authorized");
    }

    /** Open Food Facts, Cloudflare AI, ... unreachable or failing. */
    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<Map<String, String>> handleExternalService(ExternalServiceException e) {
        log.warn("External service failure: {} ({})", e.getMessage(), e.getCause());
        return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        String reason = e.getReason() != null ? e.getReason() : (status != null ? status.getReasonPhrase() : "Error");
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", reason));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) throws Exception {
        // Spring Security's own exceptions must reach its filters (login redirect / 401 / 403).
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;
        }
        log.error("Unhandled error in API request: {}", e.getMessage(), e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
