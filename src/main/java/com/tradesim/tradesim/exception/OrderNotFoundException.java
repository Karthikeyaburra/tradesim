package com.tradesim.tradesim.exception;

public class OrderNotFoundException extends NotFoundException {

    public OrderNotFoundException(Long orderId) {
        super("Order not found: " + orderId);
    }
}

