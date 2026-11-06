package com.tradesim.tradesim.service.market;

import com.tradesim.tradesim.domain.Money;
import com.tradesim.tradesim.domain.PricePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fetches live NSE India quotes and 5-minute intraday session history ({SYMBOL}.NS)
 * from Yahoo Finance's v8 chart endpoint without requiring external API keys.
 */
@Component
public class YahooFinanceNseClient implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceNseClient.class);
    private static final String BASE_URL = "https://query1.finance.yahoo.com";

    private final RestClient restClient;

    public YahooFinanceNseClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(4));

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(factory)
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; TradeSim/1.0)")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Override
    public Optional<ExternalQuote> fetchQuote(String symbol) {
        String nseTicker = toNseTicker(symbol);
        try {
            YahooChartEnvelope response = restClient.get()
                    .uri("/v8/finance/chart/{ticker}?interval=5m&range=1d", nseTicker)
                    .retrieve()
                    .body(YahooChartEnvelope.class);

            if (response == null || response.chart() == null
                    || response.chart().result() == null || response.chart().result().isEmpty()) {
                return Optional.empty();
            }

            YahooResult result = response.chart().result().get(0);
            if (result == null || result.meta() == null || result.meta().regularMarketPrice() == null) {
                return Optional.empty();
            }

            BigDecimal livePrice = Money.scale(result.meta().regularMarketPrice());
            if (livePrice.compareTo(Money.ZERO) <= 0) {
                return Optional.empty();
            }

            BigDecimal rawPrev = result.meta().chartPreviousClose() != null
                    ? result.meta().chartPreviousClose()
                    : result.meta().previousClose();
            BigDecimal prevClose = (rawPrev != null && rawPrev.compareTo(Money.ZERO) > 0)
                    ? Money.scale(rawPrev)
                    : livePrice;

            List<PricePoint> series = extractIntradayPoints(result);
            return Optional.of(new ExternalQuote(symbol, livePrice, prevClose, series));
        } catch (Exception ex) {
            log.debug("Upstream NSE quote unavailable for {} ({}): {}", symbol, nseTicker, ex.getMessage());
            return Optional.empty();
        }
    }

    private String toNseTicker(String symbol) {
        String clean = symbol.trim().toUpperCase();
        if (clean.contains(".")) {
            return clean;
        }
        return clean + ".NS";
    }

    private List<PricePoint> extractIntradayPoints(YahooResult result) {
        List<PricePoint> points = new ArrayList<>();
        List<Long> timestamps = result.timestamp();
        if (timestamps == null || timestamps.isEmpty()
                || result.indicators() == null
                || result.indicators().quote() == null
                || result.indicators().quote().isEmpty()) {
            return points;
        }

        List<BigDecimal> closes = result.indicators().quote().get(0).close();
        if (closes == null) {
            return points;
        }

        int count = Math.min(timestamps.size(), closes.size());
        for (int i = 0; i < count; i++) {
            Long epochSec = timestamps.get(i);
            BigDecimal close = closes.get(i);
            if (epochSec != null && close != null && close.compareTo(BigDecimal.ZERO) > 0) {
                points.add(new PricePoint(Money.scale(close), Instant.ofEpochSecond(epochSec)));
            }
        }
        return points;
    }

    public record YahooChartEnvelope(YahooChart chart) {
    }

    public record YahooChart(List<YahooResult> result) {
    }

    public record YahooResult(YahooMeta meta, List<Long> timestamp, YahooIndicators indicators) {
    }

    public record YahooMeta(BigDecimal regularMarketPrice, BigDecimal chartPreviousClose, BigDecimal previousClose) {
    }

    public record YahooIndicators(List<YahooQuoteSeries> quote) {
    }

    public record YahooQuoteSeries(List<BigDecimal> close) {
    }
}

