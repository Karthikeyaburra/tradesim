package com.tradesim.tradesim.dto;

import com.tradesim.tradesim.domain.Position;

import java.math.BigDecimal;

public record HoldingResponse(
        String symbol,
        String name,
        String sector,
        int quantity,
        int reservedQuantity,
        int availableQuantity,
        BigDecimal avgBuyPrice,
        BigDecimal currentPrice,
        BigDecimal costBasis,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        BigDecimal unrealizedPnlPercent
) {
    public static HoldingResponse from(Position p) {
        return new HoldingResponse(
                p.symbol(),
                p.name(),
                p.sector(),
                p.quantity(),
                p.reservedQuantity(),
                p.availableQuantity(),
                p.avgBuyPrice(),
                p.currentPrice(),
                p.costBasis(),
                p.marketValue(),
                p.unrealizedPnl(),
                p.unrealizedPnlPercent()
        );
    }
}

