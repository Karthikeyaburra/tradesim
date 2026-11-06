package com.tradesim.tradesim.dto;

import com.tradesim.tradesim.domain.Order;
import com.tradesim.tradesim.domain.OrderSide;
import com.tradesim.tradesim.domain.OrderStatus;
import com.tradesim.tradesim.domain.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long id,
        String symbol,
        OrderSide side,
        OrderType type,
        int quantity,
        BigDecimal limitPrice,
        BigDecimal executedPrice,
        BigDecimal totalValue,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.symbol(),
                order.side(),
                order.type(),
                order.quantity(),
                order.limitPrice(),
                order.executedPrice(),
                order.totalValue(),
                order.status(),
                order.createdAt(),
                order.updatedAt()
        );
    }
}

