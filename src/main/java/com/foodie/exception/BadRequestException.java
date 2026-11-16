package com.foodie.exception;

/**
 * The request was understood but its content is invalid. Reported as HTTP 400 and the message
 * is shown to the user, so it must be written to be user-readable.
 *
 * <p>Extends {@link IllegalArgumentException} so existing callers that catch that type still work,
 * but the exception handlers only trust <em>this</em> type: a bare {@code IllegalArgumentException}
 * from a library is treated as a server bug and its message is not exposed.
 */
public class BadRequestException extends IllegalArgumentException {

    public BadRequestException(String message) {
        super(message);
    }
}
