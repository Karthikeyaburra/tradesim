package com.tradesim.tradesim.exception;

/** Request is well-formed JSON but breaks a business rule (e.g. limit price on a market order) -> 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}

