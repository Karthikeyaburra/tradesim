package com.tradesim.tradesim.exception;

/** Base for "the thing you asked for does not exist" -> HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}

