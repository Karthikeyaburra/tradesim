package com.tradesim.tradesim.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Wallet(
        Long userId,
        BigDecimal balance,
        BigDecimal reservedCash,
        BigDecimal netDeposits,
        Instant updatedAt
) {
    /** Spendable cash plus cash locked by pending BUY limit orders. Must equal the ledger sum. */
    public BigDecimal totalCash() {
        return balance.add(reservedCash);
    }
}
