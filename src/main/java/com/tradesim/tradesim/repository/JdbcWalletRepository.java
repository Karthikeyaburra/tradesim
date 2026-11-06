package com.tradesim.tradesim.repository;

import com.tradesim.tradesim.domain.Wallet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JdbcWalletRepository implements WalletRepository {

    private static final RowMapper<Wallet> ROW_MAPPER = (rs, rowNum) -> new Wallet(
            rs.getLong("user_id"),
            rs.getBigDecimal("balance"),
            rs.getBigDecimal("reserved_cash"),
            rs.getBigDecimal("net_deposits"),
            rs.getTimestamp("updated_at").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcWalletRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void create(Long userId, BigDecimal initialBalance, Instant now) {
        jdbcTemplate.update(
                "INSERT INTO wallets (user_id, balance, reserved_cash, net_deposits, updated_at) VALUES (?, ?, 0.00, ?, ?)",
                userId, initialBalance, initialBalance, Timestamp.from(now)
        );
    }

    @Override
    public Optional<Wallet> findByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT user_id, balance, reserved_cash, net_deposits, updated_at FROM wallets WHERE user_id = ?",
                ROW_MAPPER,
                userId
        ).stream().findFirst();
    }

    @Override
    public boolean debitCash(Long userId, BigDecimal amount, Instant now) {
        int rows = jdbcTemplate.update(
                "UPDATE wallets SET balance = balance - ?, updated_at = ? WHERE user_id = ? AND balance >= ?",
                amount, Timestamp.from(now), userId, amount
        );
        return rows == 1;
    }

    @Override
    public void creditCash(Long userId, BigDecimal amount, Instant now) {
        jdbcTemplate.update(
                "UPDATE wallets SET balance = balance + ?, updated_at = ? WHERE user_id = ?",
                amount, Timestamp.from(now), userId
        );
    }

    @Override
    public void depositCash(Long userId, BigDecimal amount, Instant now) {
        jdbcTemplate.update(
                "UPDATE wallets SET balance = balance + ?, net_deposits = net_deposits + ?, updated_at = ? WHERE user_id = ?",
                amount, amount, Timestamp.from(now), userId
        );
    }

    @Override
    public boolean reserveCash(Long userId, BigDecimal amount, Instant now) {
        int rows = jdbcTemplate.update(
                "UPDATE wallets SET balance = balance - ?, reserved_cash = reserved_cash + ?, updated_at = ? " +
                        "WHERE user_id = ? AND balance >= ?",
                amount, amount, Timestamp.from(now), userId, amount
        );
        return rows == 1;
    }

    @Override
    public void releaseReservedCash(Long userId, BigDecimal reservedAmount, BigDecimal refundToBalance, Instant now) {
        jdbcTemplate.update(
                "UPDATE wallets SET reserved_cash = reserved_cash - ?, balance = balance + ?, updated_at = ? WHERE user_id = ?",
                reservedAmount, refundToBalance, Timestamp.from(now), userId
        );
    }

    @Override
    public void resetCash(Long userId, BigDecimal newBalance, Instant now) {
        jdbcTemplate.update(
                "UPDATE wallets SET balance = ?, reserved_cash = 0.00, net_deposits = ?, updated_at = ? WHERE user_id = ?",
                newBalance, newBalance, Timestamp.from(now), userId
        );
    }
}
