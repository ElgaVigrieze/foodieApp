package com.foodie.exception;

/**
 * A user-supplied URL was rejected because it could reach internal or otherwise non-public
 * infrastructure (SSRF protection). Extends {@link IllegalArgumentException} so it is reported
 * as a 400 by the existing exception handlers.
 */
public class UnsafeUrlException extends IllegalArgumentException {
    public UnsafeUrlException(String message) {
        super(message);
    }
}
