package com.tradesim.tradesim.repository;

import com.tradesim.tradesim.domain.PricePoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class JdbcPriceHistoryRepository implements PriceHistoryRepository {

    private static final RowMapper<PricePoint> ROW_MAPPER = (rs, rowNum) -> new PricePoint(
            rs.getBigDecimal("price"),
            rs.getTimestamp("recorded_at").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcPriceHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(String symbol, BigDecimal price, Instant recordedAt) {
        jdbcTemplate.update(
                "INSERT INTO price_history (symbol, price, recorded_at) VALUES (?, ?, ?)",
                symbol, price, Timestamp.from(recordedAt)
        );
    }

    @Override
    public void replaceSeries(String symbol, List<PricePoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        jdbcTemplate.update("DELETE FROM price_history WHERE symbol = ?", symbol);
        jdbcTemplate.batchUpdate(
                "INSERT INTO price_history (symbol, price, recorded_at) VALUES (?, ?, ?)",
                points,
                points.size(),
                (ps, pt) -> {
                    ps.setString(1, symbol);
                    ps.setBigDecimal(2, pt.price());
                    ps.setTimestamp(3, Timestamp.from(pt.recordedAt()));
                }
        );
    }

    @Override
    public List<PricePoint> findRecentBySymbol(String symbol, int limit) {
        List<PricePoint> newestFirst = jdbcTemplate.query(
                "SELECT price, recorded_at FROM price_history WHERE symbol = ? ORDER BY recorded_at DESC, id DESC LIMIT ?",
                ROW_MAPPER,
                symbol,
                limit
        );
        List<PricePoint> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return chronological;
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        return jdbcTemplate.update(
                "DELETE FROM price_history WHERE recorded_at < ?",
                Timestamp.from(cutoff)
        );
    }
}

