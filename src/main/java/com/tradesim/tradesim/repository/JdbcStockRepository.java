package com.tradesim.tradesim.repository;

import com.tradesim.tradesim.domain.Stock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcStockRepository implements StockRepository {

    private static final RowMapper<Stock> ROW_MAPPER = (rs, rowNum) -> new Stock(
            rs.getString("symbol"),
            rs.getString("name"),
            rs.getBigDecimal("price"),
            rs.getBigDecimal("previous_close"),
            rs.getString("sector")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcStockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Stock> findAll() {
        return jdbcTemplate.query(
                "SELECT symbol, name, price, previous_close, sector FROM stocks WHERE active = TRUE ORDER BY symbol",
                ROW_MAPPER
        );
    }

    @Override
    public Optional<Stock> findBySymbol(String symbol) {
        return jdbcTemplate.query(
                "SELECT symbol, name, price, previous_close, sector FROM stocks WHERE symbol = ? AND active = TRUE",
                ROW_MAPPER,
                symbol
        ).stream().findFirst();
    }

    @Override
    public boolean existsBySymbol(String symbol) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stocks WHERE symbol = ? AND active = TRUE",
                Integer.class,
                symbol
        );
        return count != null && count > 0;
    }

    @Override
    public void save(Stock stock) {
        int updated = jdbcTemplate.update(
                "UPDATE stocks SET name = ?, price = ?, previous_close = ?, sector = ?, active = TRUE WHERE symbol = ?",
                stock.name(), stock.price(), stock.previousClose(), stock.sector(), stock.symbol()
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO stocks (symbol, name, price, previous_close, sector, active) VALUES (?, ?, ?, ?, ?, TRUE)",
                    stock.symbol(), stock.name(), stock.price(), stock.previousClose(), stock.sector()
            );
        }
    }

    @Override
    public void updatePrice(String symbol, BigDecimal newPrice) {
        jdbcTemplate.update("UPDATE stocks SET price = ? WHERE symbol = ? AND active = TRUE", newPrice, symbol);
    }

    @Override
    public void updateQuote(String symbol, BigDecimal newPrice, BigDecimal previousClose) {
        jdbcTemplate.update(
                "UPDATE stocks SET price = ?, previous_close = ? WHERE symbol = ? AND active = TRUE",
                newPrice, previousClose, symbol
        );
    }

    @Override
    public boolean deactivate(String symbol) {
        return jdbcTemplate.update("UPDATE stocks SET active = FALSE WHERE symbol = ? AND active = TRUE", symbol) == 1;
    }
}

