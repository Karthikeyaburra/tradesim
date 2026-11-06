package com.tradesim.tradesim.repository;

import com.tradesim.tradesim.domain.LedgerEntry;
import com.tradesim.tradesim.domain.LedgerType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

@Repository
public class JdbcLedgerRepository implements LedgerRepository {

    private static final RowMapper<LedgerEntry> ROW_MAPPER = (rs, rowNum) -> new LedgerEntry(
            rs.getLong("id"),
            rs.getLong("user_id"),
            LedgerType.valueOf(rs.getString("type")),
            rs.getBigDecimal("amount"),
            rs.getBigDecimal("balance_after"),
            rs.getObject("reference_order_id", Long.class),
            rs.getString("description"),
            rs.getTimestamp("created_at").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcLedgerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LedgerEntry save(LedgerEntry entry) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO ledger_entries (user_id, type, amount, balance_after, reference_order_id, description, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, entry.userId());
            ps.setString(2, entry.type().name());
            ps.setBigDecimal(3, entry.amount());
            ps.setBigDecimal(4, entry.balanceAfter());
            if (entry.referenceOrderId() == null) {
                ps.setNull(5, Types.BIGINT);
            } else {
                ps.setLong(5, entry.referenceOrderId());
            }
            ps.setString(6, entry.description());
            ps.setTimestamp(7, Timestamp.from(entry.createdAt()));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return new LedgerEntry(
                id,
                entry.userId(),
                entry.type(),
                entry.amount(),
                entry.balanceAfter(),
                entry.referenceOrderId(),
                entry.description(),
                entry.createdAt()
        );
    }

    @Override
    public List<LedgerEntry> findByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT id, user_id, type, amount, balance_after, reference_order_id, description, created_at " +
                        "FROM ledger_entries WHERE user_id = ? ORDER BY id DESC",
                ROW_MAPPER,
                userId
        );
    }

    @Override
    public BigDecimal sumAmountByUserId(Long userId) {
        BigDecimal sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0.00) FROM ledger_entries WHERE user_id = ?",
                BigDecimal.class,
                userId
        );
        return sum != null ? sum : BigDecimal.ZERO;
    }

    @Override
    public void deleteByUserId(Long userId) {
        jdbcTemplate.update("DELETE FROM ledger_entries WHERE user_id = ?", userId);
    }
}

