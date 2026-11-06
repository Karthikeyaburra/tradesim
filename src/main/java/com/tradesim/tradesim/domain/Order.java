package com.tradesim.tradesim.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record Order(
        Long id,
        Long userId,
        String symbol,
        OrderSide side,
        OrderType type,
        int quantity,
        BigDecimal limitPrice,
        BigDecimal executedPrice,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isPending() {
        return status == OrderStatus.PENDING;
    }

    /** Cash locked by a pending BUY limit order: limitPrice x quantity. */
    public BigDecimal reservedCash() {
        return Money.times(limitPrice, quantity);
    }

    /** Value of the fill, or null while the order has not executed. */
    public BigDecimal totalValue() {
        return executedPrice == null ? null : Money.times(executedPrice, quantity);
    }

    public Order withId(Long newId) {
        return new Order(newId, userId, symbol, side, type, quantity, limitPrice, executedPrice, status, createdAt, updatedAt);
    }

    public Order filled(BigDecimal price, Instant at) {
        return new Order(id, userId, symbol, side, type, quantity, limitPrice, price.setScale(2, RoundingMode.HALF_UP),
                OrderStatus.FILLED, createdAt, at);
    }

    public Order cancelled(Instant at) {
        return new Order(id, userId, symbol, side, type, quantity, limitPrice, executedPrice, OrderStatus.CANCELLED, createdAt, at);
    }
}

