package com.tradesim.tradesim.service.order;

import com.tradesim.tradesim.domain.Order;
import com.tradesim.tradesim.domain.OrderType;
import com.tradesim.tradesim.domain.Stock;
import com.tradesim.tradesim.dto.PlaceOrderRequest;

import java.time.Instant;

/**
 * Strategy interface for order placement (OOP Polymorphism / Open-Closed Principle).
 * Market orders execute immediately against available balances; Limit orders lock
 * reservations and rest as PENDING until the matching engine fills them.
 */
public interface OrderExecutionStrategy {

    OrderType supports();

    Order execute(Long userId, Stock stock, PlaceOrderRequest request, Instant now);
}

