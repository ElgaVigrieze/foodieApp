package com.foodie.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.io.IOException;
import java.net.URI;
import java.util.NoSuchElementException;

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
 * Anything not listed here (including Spring's own 404/405/400 handling) keeps the default behaviour.
 */
@Slf4j
@ControllerAdvice(annotations = Controller.class)
public class WebExceptionHandler {

    private static final String GENERIC_ERROR = "Something went wrong. Please try again.";

    @ExceptionHandler(SecurityException.class)
    public void handleForbidden(SecurityException e, HttpServletResponse response) throws IOException {
        log.warn("Access denied: {}", e.getMessage());
        response.sendError(HttpStatus.FORBIDDEN.value(), "Not authorized");
    }

    /** Validation / state errors thrown by services; their messages are written to be user-readable. */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, NoSuchElementException.class})
    public String handleExpected(RuntimeException e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.warn("Request {} {} failed: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        String message = e.getMessage() == null ? GENERIC_ERROR : e.getMessage();
        HttpStatus status = e instanceof NoSuchElementException ? HttpStatus.NOT_FOUND
                : e instanceof IllegalStateException ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        return respond(message, status, request, response);
    }

    /** Database failures: logged with the stack trace, never shown to the user. */
    @ExceptionHandler(DataAccessException.class)
    public String handleDataAccess(DataAccessException e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.error("Database error on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
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
