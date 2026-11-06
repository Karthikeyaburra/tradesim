package com.tradesim.tradesim.dto;

import com.tradesim.tradesim.domain.LedgerEntry;
import com.tradesim.tradesim.domain.LedgerType;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryResponse(
        Long id,
        LedgerType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Long referenceOrderId,
        String description,
        Instant createdAt
) {
    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.id(),
                entry.type(),
                entry.amount(),
                entry.balanceAfter(),
                entry.referenceOrderId(),
                entry.description(),
                entry.createdAt()
        );
    }
}

