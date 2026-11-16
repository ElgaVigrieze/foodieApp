package com.foodie.exception;

/**
 * The current user is not allowed to access or change a record (or is not logged in).
 * Reported as HTTP 403. The message is for logs only and is never shown to the client.
 */
public class ForbiddenException extends SecurityException {

    public ForbiddenException(String message) {
        super(message);
    }
}
