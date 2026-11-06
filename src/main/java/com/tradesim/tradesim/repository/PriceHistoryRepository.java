package com.tradesim.tradesim.repository;

import com.tradesim.tradesim.domain.PricePoint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface PriceHistoryRepository {
    void record(String symbol, BigDecimal price, Instant recordedAt);

    void replaceSeries(String symbol, List<PricePoint> points);

    List<PricePoint> findRecentBySymbol(String symbol, int limit);

    int deleteOlderThan(Instant cutoff);
}

