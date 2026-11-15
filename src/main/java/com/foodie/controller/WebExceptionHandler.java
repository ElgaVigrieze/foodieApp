package com.foodie.controller;

import com.foodie.exception.BadRequestException;
import com.foodie.exception.ConflictException;
import com.foodie.exception.ExternalServiceException;
import com.foodie.exception.ForbiddenException;
import com.foodie.exception.NotFoundException;
import com.foodie.exception.UnauthenticatedException;
import com.foodie.exception.UnsafeUrlException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.io.IOException;
import java.net.URI;
import java.time.DateTimeException;

/**
 * Safety net for server-rendered (MVC) controllers. JSON endpoints are handled by
 * {@link ApiExceptionHandler}, which takes precedence for {@code @RestController}s.
 *
 * <p>Controllers that need a specific redirect target after an expected failure (for example
 * {@code BrainController}, which returns to the month being viewed) still catch those exceptions
 * themselves. This class handles whatever escapes them:
 * <ul>
 *   <li>form submissions (non-GET): the user is sent back to the page they came from with a
 *       flash {@code error} message;</li>
 *   <li>page loads (GET): a plain HTTP error, since redirecting back could loop.</li>
 * </ul>
 * Only the application's typed exceptions have their messages shown to the user. Any other
 * exception is logged with its stack trace and reported with a generic message. Spring's own
 * 404/405 handling and Spring Security's exceptions are passed through untouched.
 */
@Slf4j
@ControllerAdvice(annotations = Controller.class)
public class WebExceptionHandler {

    private static final String GENERIC_ERROR = "Something went wrong. Please try again.";

    @ExceptionHandler(UnauthenticatedException.class)
    public void handleUnauthenticated(UnauthenticatedException e, HttpServletResponse response) throws IOException {
        log.warn("Unauthenticated request: {}", e.getMessage());
        response.sendError(HttpStatus.UNAUTHORIZED.value(), "Please log in.");
    }

    @ExceptionHandler({ForbiddenException.class, SecurityException.class})
    public void handleForbidden(SecurityException e, HttpServletResponse response) throws IOException {
        log.warn("Access denied: {}", e.getMessage());
        response.sendError(HttpStatus.FORBIDDEN.value(), "Not authorized");
    }

    /** Validation / state errors thrown by services; their messages are written to be user-readable. */
    @ExceptionHandler({BadRequestException.class, UnsafeUrlException.class, NotFoundException.class,
            ConflictException.class})
    public String handleExpected(RuntimeException e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.warn("Request {} {} failed: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        String message = e.getMessage() == null ? GENERIC_ERROR : e.getMessage();
        HttpStatus status = e instanceof NotFoundException ? HttpStatus.NOT_FOUND
                : e instanceof ConflictException ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        return respond(message, status, request, response);
    }

    /**
     * A submitted form failed validation or could not be converted (for example a negative quantity
     * or a malformed date). Reported like other expected errors: back to the page with a message.
     */
    @ExceptionHandler(org.springframework.validation.BindException.class)
    public String handleInvalidForm(org.springframework.validation.BindException e, HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        var fieldError = e.getBindingResult().getFieldError();
        String message = "Please check the form and try again.";
        if (fieldError != null) {
            message = fieldError.isBindingFailure()
                    ? "Invalid value for " + fieldError.getField()
                    : fieldError.getField() + ": " + fieldError.getDefaultMessage();
        }
        log.warn("Invalid form on {} {}: {}", request.getMethod(), request.getRequestURI(), message);
        return respond(message, HttpStatus.BAD_REQUEST, request, response);
    }

    /** A required form field or file part is missing, or a field has the wrong type. */
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class})
    public String handleBadParameter(Exception e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.warn("Bad parameter on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        String message = "Please check the form and try again.";
        if (e instanceof MissingServletRequestParameterException missing) {
            message = "Missing required field: " + missing.getParameterName();
        } else if (e instanceof MissingServletRequestPartException missing) {
            message = "Missing required file: " + missing.getRequestPartName();
        } else if (e instanceof MethodArgumentTypeMismatchException mismatch) {
            message = "Invalid value for " + mismatch.getName();
        }
        return respond(message, HttpStatus.BAD_REQUEST, request, response);
    }

    /** Unparseable numbers/dates (their own messages are not user-friendly). */
    @ExceptionHandler({NumberFormatException.class, DateTimeException.class})
    public String handleMalformedValue(RuntimeException e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.warn("Malformed value on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return respond("Invalid value in the form.", HttpStatus.BAD_REQUEST, request, response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleUploadTooLarge(MaxUploadSizeExceededException e, HttpServletRequest request,
                                       HttpServletResponse response) throws IOException {
        log.warn("Upload too large on {} {}", request.getMethod(), request.getRequestURI());
        return respond("That file is too large.", HttpStatus.PAYLOAD_TOO_LARGE, request, response);
    }

    /** An external service (Open Food Facts, Cloudflare AI, ...) is down; the message says which. */
    @ExceptionHandler(ExternalServiceException.class)
    public String handleExternalService(ExternalServiceException e, HttpServletRequest request,
                                        HttpServletResponse response) throws IOException {
        log.warn("External service failure on {} {}: {} ({})", request.getMethod(), request.getRequestURI(),
                e.getMessage(), e.getCause());
        return respond(e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE, request, response);
    }

    /** A constraint rejected the write (duplicate, still referenced, ...). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrity(DataIntegrityViolationException e, HttpServletRequest request,
                                      HttpServletResponse response) throws IOException {
        log.warn("Data integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(),
                e.getMostSpecificCause().getMessage());
        return respond("That conflicts with existing data (for example a duplicate, or it is still in use).",
                HttpStatus.CONFLICT, request, response);
    }

    /** Database failures: logged with the stack trace, never shown to the user. */
    @ExceptionHandler(DataAccessException.class)
    public String handleDataAccess(DataAccessException e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.error("Database error on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return respond(GENERIC_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, request, response);
    }

    /** Anything unexpected: logged with the stack trace, never shown to the user. */
    @ExceptionHandler(Exception.class)
    public String handleUnexpected(Exception e, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        // Spring Security's exceptions and Spring MVC's own status exceptions (404, 405, ...)
        // keep their default handling.
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException
                || e instanceof ErrorResponse) {
            throw e;
        }
        log.error("Unhandled error on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return respond(GENERIC_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, request, response);
    }

    private String respond(String message, HttpStatus status, HttpServletRequest request,
                           HttpServletResponse response) throws IOException {
        if (HttpMethod.GET.matches(request.getMethod())) {
            response.sendError(status.value(), message);
            return null;
        }
        RequestContextUtils.getOutputFlashMap(request).put("error", message);
        return "redirect:" + backTarget(request);
    }

    /** Path (and query) of the Referer, so we can never redirect off-site. Falls back to the home page. */
    private static String backTarget(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (StringUtils.hasText(referer)) {
            try {
                URI uri = URI.create(referer);
                if (uri.getPath() != null && !uri.getPath().isEmpty()) {
                    return uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
                }
            } catch (IllegalArgumentException ignored) {
                // malformed header: fall through to the default
            }
        }
        return "/";
    }
}
