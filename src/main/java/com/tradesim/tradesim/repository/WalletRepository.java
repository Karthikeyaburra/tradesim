package com.tradesim.tradesim.repository;

import com.tradesim.tradesim.domain.Wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface WalletRepository {
    void create(Long userId, BigDecimal initialBalance, Instant now);

    Optional<Wallet> findByUserId(Long userId);

    /** Atomically deducts amount ONLY IF balance >= amount. */
    boolean debitCash(Long userId, BigDecimal amount, Instant now);

    /** Atomically adds trade proceeds to balance (does not change net_deposits). */
    void creditCash(Long userId, BigDecimal amount, Instant now);

    /** Atomically adds an admin deposit to both balance and net_deposits. */
    void depositCash(Long userId, BigDecimal amount, Instant now);

    /** Atomically moves amount from balance -> reserved_cash ONLY IF balance >= amount. */
    boolean reserveCash(Long userId, BigDecimal amount, Instant now);

    /** Releases reservedAmount from reserved_cash and adds refundToBalance back to balance. */
    void releaseReservedCash(Long userId, BigDecimal reservedAmount, BigDecimal refundToBalance, Instant now);

    /** Resets balance and net_deposits to newBalance and reserved_cash to 0.00. */
    void resetCash(Long userId, BigDecimal newBalance, Instant now);
}
