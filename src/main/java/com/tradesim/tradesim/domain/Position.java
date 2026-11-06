package com.tradesim.tradesim.domain;

import java.math.BigDecimal;

/** A trader's position in one stock, joined with the stock's live price. */
public record Position(
        String symbol,
        String name,
        String sector,
        int quantity,
        int reservedQuantity,
        BigDecimal avgBuyPrice,
        BigDecimal currentPrice
) {
    public int availableQuantity() {
        return quantity - reservedQuantity;
    }

    public BigDecimal costBasis() {
        return Money.times(avgBuyPrice, quantity);
    }

    public BigDecimal marketValue() {
        return Money.times(currentPrice, quantity);
    }

    public BigDecimal unrealizedPnl() {
        return marketValue().subtract(costBasis());
    }

    public BigDecimal unrealizedPnlPercent() {
        return Money.percent(unrealizedPnl(), costBasis());
    }
}

