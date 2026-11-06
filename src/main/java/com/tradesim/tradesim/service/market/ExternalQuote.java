package com.tradesim.tradesim.service.market;

import com.tradesim.tradesim.domain.PricePoint;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable real-market snapshot returned by a {@link MarketDataProvider}.
 */
public record ExternalQuote(
        String symbol,
        BigDecimal price,
        BigDecimal previousClose,
        List<PricePoint> intradayHistory
) {
}

