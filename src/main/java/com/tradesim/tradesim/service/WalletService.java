package com.tradesim.tradesim.service;

import com.tradesim.tradesim.domain.LedgerEntry;
import com.tradesim.tradesim.domain.LedgerType;
import com.tradesim.tradesim.domain.Money;
import com.tradesim.tradesim.domain.Wallet;
import com.tradesim.tradesim.dto.LedgerEntryResponse;
import com.tradesim.tradesim.dto.WalletResponse;
import com.tradesim.tradesim.exception.InsufficientFundsException;
import com.tradesim.tradesim.repository.LedgerRepository;
import com.tradesim.tradesim.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class WalletService {

    /** ₹10,00,000.00 starting virtual balance for every TRADER. */
    public static final BigDecimal INITIAL_BALANCE = Money.of("1000000.00");

    private final WalletRepository walletRepository;
    private final LedgerRepository ledgerRepository;

    public WalletService(WalletRepository walletRepository, LedgerRepository ledgerRepository) {
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public void createInitialWallet(Long userId) {
        Instant now = Instant.now();
        walletRepository.create(userId, INITIAL_BALANCE, now);
        ledgerRepository.save(new LedgerEntry(
                null,
                userId,
                LedgerType.INITIAL_CREDIT,
                INITIAL_BALANCE,
                INITIAL_BALANCE,
                null,
                "Opening virtual trading balance",
                now
        ));
    }

    public boolean isLedgerConsistent(Long userId) {
        Wallet wallet = getWallet(userId);
        BigDecimal ledgerSum = ledgerRepository.sumAmountByUserId(userId);
        return wallet.totalCash().compareTo(ledgerSum) == 0;
    }

    public Wallet getWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found for trader " + userId));
    }

    /** Atomically debits available cash or throws 409 InsufficientFundsException. */
    @Transactional
    public Wallet debitOrThrow(Long userId, BigDecimal amount, LedgerType type, Long orderId, String description, Instant now) {
        BigDecimal scaled = Money.scale(amount);
        if (!walletRepository.debitCash(userId, scaled, now)) {
            BigDecimal available = walletRepository.findByUserId(userId)
                    .map(Wallet::balance)
                    .orElse(Money.ZERO);
            throw new InsufficientFundsException(scaled, available);
        }
        Wallet updated = getWallet(userId);
        ledgerRepository.save(new LedgerEntry(
                null, userId, type, scaled.negate(), updated.totalCash(), orderId, description, now
        ));
        return updated;
    }

    /** Atomically credits trade/settlement proceeds and appends a ledger entry. */
    @Transactional
    public Wallet creditProceeds(Long userId, BigDecimal amount, LedgerType type, Long orderId, String description, Instant now) {
        BigDecimal scaled = Money.scale(amount);
        walletRepository.creditCash(userId, scaled, now);
        Wallet updated = getWallet(userId);
        ledgerRepository.save(new LedgerEntry(
                null, userId, type, scaled, updated.totalCash(), orderId, description, now
        ));
        return updated;
    }

    /** Atomically locks cash for a pending Limit BUY or throws 409 InsufficientFundsException. */
    @Transactional
    public void reserveOrThrow(Long userId, BigDecimal amount, Instant now) {
        BigDecimal scaled = Money.scale(amount);
        if (!walletRepository.reserveCash(userId, scaled, now)) {
            BigDecimal available = walletRepository.findByUserId(userId)
                    .map(Wallet::balance)
                    .orElse(Money.ZERO);
            throw new InsufficientFundsException(scaled, available);
        }
    }

    /** Releases locked cash when a Limit BUY fills (settling actualCost into the ledger and refunding any price improvement). */
    @Transactional
    public void settleReservedBuy(Long userId, BigDecimal reservedAmount, BigDecimal actualCost, Long orderId, String description, Instant now) {
        BigDecimal refund = Money.scale(reservedAmount.subtract(actualCost));
        walletRepository.releaseReservedCash(userId, Money.scale(reservedAmount), refund, now);
        Wallet updated = getWallet(userId);
        ledgerRepository.save(new LedgerEntry(
                null, userId, LedgerType.TRADE_BUY, Money.scale(actualCost).negate(), updated.totalCash(), orderId, description, now
        ));
    }

    /** Unlocks reserved cash back into available balance when a Limit BUY is cancelled. */
    @Transactional
    public void cancelReservedBuy(Long userId, BigDecimal reservedAmount, Instant now) {
        BigDecimal scaled = Money.scale(reservedAmount);
        walletRepository.releaseReservedCash(userId, scaled, scaled, now);
    }

    /** Admin cash deposit: updates both available cash and net_deposits, plus an audit ledger entry. */
    @Transactional
    public WalletResponse adminDeposit(Long userId, BigDecimal amount) {
        Instant now = Instant.now();
        BigDecimal scaled = Money.scale(amount);
        walletRepository.depositCash(userId, scaled, now);
        Wallet updated = getWallet(userId);
        ledgerRepository.save(new LedgerEntry(
                null, userId, LedgerType.ADMIN_DEPOSIT, scaled, updated.totalCash(), null,
                "Admin deposit of ₹" + scaled.toPlainString(), now
        ));
        return getWalletView(userId);
    }

    /** Append-only reset: adjusts wallet back to INITIAL_BALANCE and records the exact delta in ledger_entries. */
    @Transactional
    public WalletResponse resetToInitialBalance(Long userId, Instant now) {
        Wallet before = getWallet(userId);
        BigDecimal delta = INITIAL_BALANCE.subtract(before.totalCash());
        walletRepository.resetCash(userId, INITIAL_BALANCE, now);
        ledgerRepository.save(new LedgerEntry(
                null, userId, LedgerType.ACCOUNT_RESET, delta, INITIAL_BALANCE, null,
                "Admin account reset to ₹" + INITIAL_BALANCE.toPlainString(), now
        ));
        return getWalletView(userId);
    }

    public WalletResponse getWalletView(Long userId) {
        Wallet wallet = getWallet(userId);
        List<LedgerEntryResponse> entries = ledgerRepository.findByUserId(userId).stream()
                .map(LedgerEntryResponse::from)
                .toList();
        return WalletResponse.of(wallet, isLedgerConsistent(userId), entries);
    }
}
