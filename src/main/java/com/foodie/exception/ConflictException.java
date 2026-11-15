package com.foodie.exception;

/** The request is valid but conflicts with the current state of the data. Reported as HTTP 409. */
public class ConflictException extends IllegalStateException {

    public ConflictException(String message) {
        super(message);
    }
}
