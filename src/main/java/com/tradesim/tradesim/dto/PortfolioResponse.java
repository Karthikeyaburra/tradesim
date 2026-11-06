package com.tradesim.tradesim.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioResponse(
        BigDecimal cash,
        BigDecimal reservedCash,
        BigDecimal holdingsValue,
        BigDecimal totalValue,
        BigDecimal netDeposits,
        BigDecimal totalPnl,
        BigDecimal totalPnlPercent,
        List<HoldingResponse> holdings
) {
}

