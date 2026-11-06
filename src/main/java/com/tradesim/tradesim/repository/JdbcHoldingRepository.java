package com.tradesim.tradesim.repository;

import com.tradesim.tradesim.domain.Position;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcHoldingRepository implements HoldingRepository {

    private static final RowMapper<RawHolding> RAW_MAPPER = (rs, rowNum) -> new RawHolding(
            rs.getLong("user_id"),
            rs.getString("symbol"),
            rs.getInt("quantity"),
            rs.getInt("reserved_quantity"),
            rs.getBigDecimal("avg_buy_price")
    );

    private static final RowMapper<Position> POSITION_MAPPER = (rs, rowNum) -> new Position(
            rs.getString("symbol"),
            rs.getString("name"),
            rs.getString("sector"),
            rs.getInt("quantity"),
            rs.getInt("reserved_quantity"),
            rs.getBigDecimal("avg_buy_price"),
            rs.getBigDecimal("current_price")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcHoldingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<RawHolding> findRaw(Long userId, String symbol) {
        return jdbcTemplate.query(
                "SELECT user_id, symbol, quantity, reserved_quantity, avg_buy_price FROM holdings WHERE user_id = ? AND symbol = ?",
                RAW_MAPPER,
                userId, symbol
        ).stream().findFirst();
    }

    @Override
    public List<Position> findPositionsByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT h.symbol, s.name, s.sector, h.quantity, h.reserved_quantity, h.avg_buy_price, s.price AS current_price " +
                        "FROM holdings h JOIN stocks s ON s.symbol = h.symbol " +
                        "WHERE h.user_id = ? AND h.quantity > 0 ORDER BY h.symbol",
                POSITION_MAPPER,
                userId
        );
    }

    @Override
    public List<RawHolding> findAllHoldersOfSymbol(String symbol) {
        return jdbcTemplate.query(
                "SELECT user_id, symbol, quantity, reserved_quantity, avg_buy_price FROM holdings WHERE symbol = ? AND quantity > 0",
                RAW_MAPPER,
                symbol
        );
    }

    @Override
    public void addShares(Long userId, String symbol, int addedQty, BigDecimal executionPrice) {
        // Single atomic MySQL UPSERT computing exact weighted average buy price in SQL!
        jdbcTemplate.update(
                "INSERT INTO holdings (user_id, symbol, quantity, reserved_quantity, avg_buy_price) VALUES (?, ?, ?, 0, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "avg_buy_price = ROUND(((quantity * avg_buy_price) + (? * ?)) / (quantity + ?), 2), " +
                        "quantity = quantity + ?",
                userId, symbol, addedQty, executionPrice,
                addedQty, executionPrice, addedQty,
                addedQty
        );
    }

    @Override
    public boolean removeUnreservedShares(Long userId, String symbol, int soldQty) {
        int rows = jdbcTemplate.update(
                "UPDATE holdings SET quantity = quantity - ? " +
                        "WHERE user_id = ? AND symbol = ? AND (quantity - reserved_quantity) >= ?",
                soldQty, userId, symbol, soldQty
        );
        if (rows == 1) {
            cleanupZeroRow(userId, symbol);
            return true;
        }
        return false;
    }

    @Override
    public boolean reserveShares(Long userId, String symbol, int qty) {
        int rows = jdbcTemplate.update(
                "UPDATE holdings SET reserved_quantity = reserved_quantity + ? " +
                        "WHERE user_id = ? AND symbol = ? AND (quantity - reserved_quantity) >= ?",
                qty, userId, symbol, qty
        );
        return rows == 1;
    }

    @Override
    public void releaseReservedShares(Long userId, String symbol, int qty) {
        jdbcTemplate.update(
                "UPDATE holdings SET reserved_quantity = reserved_quantity - ? " +
                        "WHERE user_id = ? AND symbol = ? AND reserved_quantity >= ?",
                qty, userId, symbol, qty
        );
    }

    @Override
    public void consumeReservedShares(Long userId, String symbol, int qty) {
        jdbcTemplate.update(
                "UPDATE holdings SET quantity = quantity - ?, reserved_quantity = reserved_quantity - ? " +
                        "WHERE user_id = ? AND symbol = ? AND reserved_quantity >= ? AND quantity >= ?",
                qty, qty, userId, symbol, qty, qty
        );
        cleanupZeroRow(userId, symbol);
    }

    @Override
    public void deleteByUserId(Long userId) {
        jdbcTemplate.update("DELETE FROM holdings WHERE user_id = ?", userId);
    }

    @Override
    public void deleteBySymbol(String symbol) {
        jdbcTemplate.update("DELETE FROM holdings WHERE symbol = ?", symbol);
    }

    private void cleanupZeroRow(Long userId, String symbol) {
        jdbcTemplate.update(
                "DELETE FROM holdings WHERE user_id = ? AND symbol = ? AND quantity = 0 AND reserved_quantity = 0",
                userId, symbol
        );
    }
}

