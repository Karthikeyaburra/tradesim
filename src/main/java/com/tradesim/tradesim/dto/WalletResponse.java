package com.tradesim.tradesim.dto;

import com.tradesim.tradesim.domain.Wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record WalletResponse(
        BigDecimal balance,
        BigDecimal reservedCash,
        BigDecimal totalCash,
        BigDecimal netDeposits,
        boolean ledgerConsistent,
        Instant updatedAt,
        List<LedgerEntryResponse> ledger
) {
    public static WalletResponse of(Wallet wallet, boolean ledgerConsistent, List<LedgerEntryResponse> ledger) {
        return new WalletResponse(
                wallet.balance(),
                wallet.reservedCash(),
                wallet.totalCash(),
                wallet.netDeposits(),
                ledgerConsistent,
                wallet.updatedAt(),
                ledger
        );
    }
}
