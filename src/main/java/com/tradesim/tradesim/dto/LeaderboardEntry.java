package com.tradesim.tradesim.dto;

import java.math.BigDecimal;

public record LeaderboardEntry(
        int rank,
        Long userId,
        String username,
        BigDecimal cashValue,
        BigDecimal holdingsValue,
        BigDecimal totalValue,
        BigDecimal totalPnl,
        BigDecimal totalPnlPercent
) {
}

