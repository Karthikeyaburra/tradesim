package com.tradesim.tradesim.exception;

public class OrderNotPendingException extends ConflictException {

    public OrderNotPendingException(Long orderId) {
        super("Order " + orderId + " is no longer pending");
    }
}

