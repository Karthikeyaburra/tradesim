package com.tradesim.tradesim.dto;

import com.tradesim.tradesim.domain.Stock;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record StockResponse(
        String symbol,
        String name,
        BigDecimal price,
        BigDecimal previousClose,
        BigDecimal change,
        BigDecimal changePercent,
        String sector
) {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public static StockResponse from(Stock stock) {
        BigDecimal change = stock.price()
                .subtract(stock.previousClose())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal changePercent = stock.previousClose().compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : change.multiply(HUNDRED)
                .divide(stock.previousClose(), 2, RoundingMode.HALF_UP);

        return new StockResponse(
                stock.symbol(),
                stock.name(),
                stock.price(),
                stock.previousClose(),
                change,
                changePercent,
                stock.sector()
        );
    }
}