package com.tradesim.tradesim.service;

import com.tradesim.tradesim.domain.Money;
import com.tradesim.tradesim.domain.PricePoint;
import com.tradesim.tradesim.domain.Stock;
import com.tradesim.tradesim.dto.AddStockRequest;
import com.tradesim.tradesim.exception.InvalidRequestException;
import com.tradesim.tradesim.exception.StockNotFoundException;
import com.tradesim.tradesim.repository.PriceHistoryRepository;
import com.tradesim.tradesim.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class StockService {

    private final StockRepository stockRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    public StockService(StockRepository stockRepository, PriceHistoryRepository priceHistoryRepository) {
        this.stockRepository = stockRepository;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }

    public Stock getBySymbol(String symbol) {
        String normalized = normalizeSymbol(symbol);
        return stockRepository.findBySymbol(normalized)
                .orElseThrow(() -> new StockNotFoundException(normalized));
    }

    public List<PricePoint> getHistory(String symbol, int limit) {
        if (limit < 1 || limit > 1000) {
            throw new InvalidRequestException("History limit must be between 1 and 1000");
        }
        Stock stock = getBySymbol(symbol);
        return priceHistoryRepository.findRecentBySymbol(stock.symbol(), limit);
    }

    @Transactional
    public Stock addOrRelistStock(AddStockRequest request) {
        String symbol = normalizeSymbol(request.symbol());
        BigDecimal price = Money.scale(request.price());
        Stock stock = new Stock(symbol, request.name().trim(), price, price, request.sector().trim());
        stockRepository.save(stock);
        priceHistoryRepository.record(symbol, price, Instant.now());
        return stock;
    }

    public static String normalizeSymbol(String rawSymbol) {
        return rawSymbol.trim().toUpperCase(Locale.ROOT);
    }
}
