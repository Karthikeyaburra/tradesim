package com.tradesim.tradesim.repository;

import com.tradesim.tradesim.domain.LedgerEntry;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerRepository {
    LedgerEntry save(LedgerEntry entry);

    List<LedgerEntry> findByUserId(Long userId);

    BigDecimal sumAmountByUserId(Long userId);

    void deleteByUserId(Long userId);
}

