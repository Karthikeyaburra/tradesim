package com.tradesim.tradesim.exception;

/** Base for "valid request, but the current state forbids it" -> HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

