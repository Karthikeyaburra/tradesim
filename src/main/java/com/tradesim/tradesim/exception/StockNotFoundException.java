package com.tradesim.tradesim.exception;

public class StockNotFoundException extends NotFoundException {

    public StockNotFoundException(String symbol) {
        super("Stock not found: " + symbol);
    }
}
