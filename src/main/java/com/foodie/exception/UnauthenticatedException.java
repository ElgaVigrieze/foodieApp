package com.foodie.exception;

/** There is no logged-in user (or the session's user no longer exists). Reported as HTTP 401. */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
