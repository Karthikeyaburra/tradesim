package com.tradesim.tradesim.repository;

import com.tradesim.tradesim.domain.Stock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StockRepository {
    List<Stock> findAll();

    Optional<Stock> findBySymbol(String symbol);

    boolean existsBySymbol(String symbol);

    void save(Stock stock);

    void updatePrice(String symbol, BigDecimal newPrice);

    void updateQuote(String symbol, BigDecimal newPrice, BigDecimal previousClose);

    boolean deactivate(String symbol);
}
