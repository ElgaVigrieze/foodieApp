package com.foodie.service;

/** Thrown when a browser recorder presents a token that does not belong to any user. */
public class InvalidBrainTokenException extends RuntimeException {

    public InvalidBrainTokenException() {
        super("Recorder token is invalid or has been regenerated");
    }
}
