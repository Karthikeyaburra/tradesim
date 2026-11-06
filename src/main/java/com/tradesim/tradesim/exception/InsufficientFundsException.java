package com.tradesim.tradesim.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends ConflictException {

    public InsufficientFundsException(BigDecimal required, BigDecimal available) {
        super("Insufficient funds: need " + required.toPlainString() + ", available " + available.toPlainString());
    }
}

