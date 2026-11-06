package com.tradesim.tradesim.service.market;

import java.util.Optional;

/**
 * Strategy interface for upstream market data feeds (e.g., NSE via Yahoo Finance, Finnhub, or mock feeds).
 * Decouples market synchronization from any specific external HTTP API.
 */
public interface MarketDataProvider {
    Optional<ExternalQuote> fetchQuote(String symbol);
}

