package com.tradesim.tradesim.repository;

import com.tradesim.tradesim.domain.Order;
import com.tradesim.tradesim.domain.OrderFilter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);

    Optional<Order> findById(Long id);

    List<Order> findByUser(Long userId, OrderFilter filter);

    /** Returns PENDING limit orders for symbol whose limit condition is crossed by currentPrice. */
    List<Order> findMatchablePendingOrders(String symbol, BigDecimal currentPrice);

    List<Order> findPendingByUserId(Long userId);

    List<Order> findPendingBySymbol(String symbol);

    /** Atomically transitions PENDING -> FILLED. Returns false if order was already filled or cancelled. */
    boolean markFilledIfPending(Long orderId, BigDecimal executedPrice, Instant updatedAt);

    /** Atomically transitions PENDING -> CANCELLED. Returns false if order was already filled or cancelled. */
    boolean markCancelledIfPending(Long orderId, Instant updatedAt);
}

