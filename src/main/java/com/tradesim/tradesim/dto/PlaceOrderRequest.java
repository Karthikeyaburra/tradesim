package com.tradesim.tradesim.dto;

import com.tradesim.tradesim.domain.OrderSide;
import com.tradesim.tradesim.domain.OrderType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PlaceOrderRequest(
        @NotBlank(message = "Symbol is required")
        String symbol,

        @NotNull(message = "Order side (BUY or SELL) is required")
        OrderSide side,

        @NotNull(message = "Order type (MARKET or LIMIT) is required")
        OrderType type,

        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 100_000, message = "Quantity cannot exceed 100,000 per order")
        int quantity,

        BigDecimal limitPrice
) {
}

