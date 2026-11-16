package com.foodie.exception;

/**
 * An external service (Open Food Facts, Cloudflare AI, ...) could not be reached or returned an
 * error. Distinct from "the service answered and had no result". Reported as HTTP 503.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
