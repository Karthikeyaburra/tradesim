package com.tradesim.tradesim.repository;

import com.tradesim.tradesim.domain.Order;
import com.tradesim.tradesim.domain.OrderFilter;
import com.tradesim.tradesim.domain.OrderSide;
import com.tradesim.tradesim.domain.OrderStatus;
import com.tradesim.tradesim.domain.OrderType;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcOrderRepository implements OrderRepository {

    private static final RowMapper<Order> ROW_MAPPER = (rs, rowNum) -> new Order(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("symbol"),
            OrderSide.valueOf(rs.getString("side")),
            OrderType.valueOf(rs.getString("type")),
            rs.getInt("quantity"),
            rs.getBigDecimal("limit_price"),
            rs.getBigDecimal("executed_price"),
            OrderStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Order save(Order order) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO orders (user_id, symbol, side, type, quantity, limit_price, executed_price, status, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, order.userId());
            ps.setString(2, order.symbol());
            ps.setString(3, order.side().name());
            ps.setString(4, order.type().name());
            ps.setInt(5, order.quantity());
            if (order.limitPrice() == null) {
                ps.setNull(6, Types.DECIMAL);
            } else {
                ps.setBigDecimal(6, order.limitPrice());
            }
            if (order.executedPrice() == null) {
                ps.setNull(7, Types.DECIMAL);
            } else {
                ps.setBigDecimal(7, order.executedPrice());
            }
            ps.setString(8, order.status().name());
            ps.setTimestamp(9, Timestamp.from(order.createdAt()));
            ps.setTimestamp(10, Timestamp.from(order.updatedAt()));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return order.withId(key != null ? key.longValue() : null);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return jdbcTemplate.query(
                "SELECT id, user_id, symbol, side, type, quantity, limit_price, executed_price, status, created_at, updated_at " +
                        "FROM orders WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    @Override
    public List<Order> findByUser(Long userId, OrderFilter filter) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, user_id, symbol, side, type, quantity, limit_price, executed_price, status, created_at, updated_at " +
                        "FROM orders WHERE user_id = ?"
        );
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (filter != null) {
            if (filter.symbol() != null && !filter.symbol().isBlank()) {
                sql.append(" AND symbol = ?");
                params.add(filter.symbol().trim().toUpperCase());
            }
            if (filter.side() != null) {
                sql.append(" AND side = ?");
                params.add(filter.side().name());
            }
            if (filter.type() != null) {
                sql.append(" AND type = ?");
                params.add(filter.type().name());
            }
            if (filter.status() != null) {
                sql.append(" AND status = ?");
                params.add(filter.status().name());
            }
            if (filter.from() != null) {
                sql.append(" AND created_at >= ?");
                params.add(Timestamp.from(filter.from()));
            }
            if (filter.toExclusive() != null) {
                sql.append(" AND created_at < ?");
                params.add(Timestamp.from(filter.toExclusive()));
            }
        }
        sql.append(" ORDER BY created_at DESC, id DESC");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    @Override
    public List<Order> findMatchablePendingOrders(String symbol, BigDecimal currentPrice) {
        return jdbcTemplate.query(
                "SELECT id, user_id, symbol, side, type, quantity, limit_price, executed_price, status, created_at, updated_at " +
                        "FROM orders WHERE status = 'PENDING' AND symbol = ? AND (" +
                        "(side = 'BUY' AND limit_price >= ?) OR (side = 'SELL' AND limit_price <= ?)" +
                        ") ORDER BY created_at ASC, id ASC",
                ROW_MAPPER,
                symbol, currentPrice, currentPrice
        );
    }

    @Override
    public List<Order> findPendingByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT id, user_id, symbol, side, type, quantity, limit_price, executed_price, status, created_at, updated_at " +
                        "FROM orders WHERE user_id = ? AND status = 'PENDING' ORDER BY id ASC",
                ROW_MAPPER,
                userId
        );
    }

    @Override
    public List<Order> findPendingBySymbol(String symbol) {
        return jdbcTemplate.query(
                "SELECT id, user_id, symbol, side, type, quantity, limit_price, executed_price, status, created_at, updated_at " +
                        "FROM orders WHERE symbol = ? AND status = 'PENDING' ORDER BY id ASC",
                ROW_MAPPER,
                symbol
        );
    }

    @Override
    public boolean markFilledIfPending(Long orderId, BigDecimal executedPrice, Instant updatedAt) {
        int rows = jdbcTemplate.update(
                "UPDATE orders SET status = 'FILLED', executed_price = ?, updated_at = ? WHERE id = ? AND status = 'PENDING'",
                executedPrice, Timestamp.from(updatedAt), orderId
        );
        return rows == 1;
    }

    @Override
    public boolean markCancelledIfPending(Long orderId, Instant updatedAt) {
        int rows = jdbcTemplate.update(
                "UPDATE orders SET status = 'CANCELLED', updated_at = ? WHERE id = ? AND status = 'PENDING'",
                Timestamp.from(updatedAt), orderId
        );
        return rows == 1;
    }
}

