package com.tradesim.tradesim.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntry(
        Long id,
        Long userId,
        LedgerType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Long referenceOrderId,
        String description,
        Instant createdAt
) {
}

