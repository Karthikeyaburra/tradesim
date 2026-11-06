package com.tradesim.tradesim.exception;

public class InsufficientSharesException extends ConflictException {

    public InsufficientSharesException(String symbol, int requested, int available) {
        super("Insufficient shares of " + symbol + ": requested " + requested + ", available " + available);
    }
}

