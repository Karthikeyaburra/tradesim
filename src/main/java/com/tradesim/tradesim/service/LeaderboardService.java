package com.tradesim.tradesim.service;

import com.tradesim.tradesim.domain.Money;
import com.tradesim.tradesim.dto.LeaderboardEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class LeaderboardService {

    private final JdbcTemplate jdbcTemplate;

    public LeaderboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Single relational JOIN + GROUP BY query computing every trader's live net worth
     * (available cash + reserved cash + market value of all held shares) sorted descending.
     */
    public List<LeaderboardEntry> getLeaderboard() {
        String sql = """
                SELECT u.id AS user_id,
                       u.username AS username,
                       (w.balance + w.reserved_cash) AS cash_value,
                       w.net_deposits AS net_deposits,
                       COALESCE(SUM(h.quantity * s.price), 0.00) AS holdings_value,
                       (w.balance + w.reserved_cash + COALESCE(SUM(h.quantity * s.price), 0.00)) AS total_value
                FROM users u
                JOIN wallets w ON w.user_id = u.id
                LEFT JOIN holdings h ON h.user_id = u.id
                LEFT JOIN stocks s ON s.symbol = h.symbol
                WHERE u.role = 'TRADER'
                GROUP BY u.id, u.username, w.balance, w.reserved_cash, w.net_deposits
                ORDER BY total_value DESC, u.id ASC
                """;

        record RawRow(Long userId, String username, BigDecimal cashValue, BigDecimal netDeposits,
                      BigDecimal holdingsValue, BigDecimal totalValue) {
        }

        List<RawRow> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new RawRow(
                rs.getLong("user_id"),
                rs.getString("username"),
                Money.scale(rs.getBigDecimal("cash_value")),
                Money.scale(rs.getBigDecimal("net_deposits")),
                Money.scale(rs.getBigDecimal("holdings_value")),
                Money.scale(rs.getBigDecimal("total_value"))
        ));

        List<LeaderboardEntry> leaderboard = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            RawRow r = rows.get(i);
            BigDecimal pnl = r.totalValue().subtract(r.netDeposits());
            BigDecimal pnlPct = Money.percent(pnl, r.netDeposits());
            leaderboard.add(new LeaderboardEntry(
                    i + 1,
                    r.userId(),
                    r.username(),
                    r.cashValue(),
                    r.holdingsValue(),
                    r.totalValue(),
                    pnl,
                    pnlPct
            ));
        }
        return leaderboard;
    }
}

