package com.foodie.exception;

import java.util.NoSuchElementException;

/** A requested record does not exist. Reported as HTTP 404. */
public class NotFoundException extends NoSuchElementException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String entity, Object id) {
        return new NotFoundException(entity + " not found: " + id);
    }
}
