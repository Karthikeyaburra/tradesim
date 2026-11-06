package com.tradesim.tradesim.domain;

/** Filters for the order history screen and CSV export. Null fields mean "any". */
public record OrderFilter(
        String symbol,
        OrderSide side,
        OrderType type,
        OrderStatus status,
        java.time.Instant from,
        java.time.Instant toExclusive
) {
}

