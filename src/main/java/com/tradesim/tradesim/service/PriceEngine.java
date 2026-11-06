package com.tradesim.tradesim.service;

import com.tradesim.tradesim.domain.Money;
import com.tradesim.tradesim.domain.Stock;
import com.tradesim.tradesim.repository.PriceHistoryRepository;
import com.tradesim.tradesim.repository.StockRepository;
import com.tradesim.tradesim.service.market.LiveMarketSyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@ConditionalOnProperty(name = "tradesim.price-engine.enabled", havingValue = "true", matchIfMissing = true)
public class PriceEngine {

    private final StockRepository stockRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final OrderService orderService;
    private final MarketStreamService marketStreamService;
    private final LiveMarketSyncService liveMarketSyncService;
    private final double volatility;
    private final BigDecimal circuitLimit;
    private final BigDecimal priceFloor;
    private final Duration retention;
    private long tickCounter = 0;

    public PriceEngine(StockRepository stockRepository,
                       PriceHistoryRepository priceHistoryRepository,
                       OrderService orderService,
                       MarketStreamService marketStreamService,
                       LiveMarketSyncService liveMarketSyncService,
                       @Value("${tradesim.price-engine.volatility:0.0012}") double volatility,
                       @Value("${tradesim.price-engine.circuit-limit:0.20}") BigDecimal circuitLimit,
                       @Value("${tradesim.price-engine.floor:1.00}") BigDecimal priceFloor,
                       @Value("${tradesim.price-history.retention-hours:24}") long retentionHours) {
        this.stockRepository = stockRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.orderService = orderService;
        this.marketStreamService = marketStreamService;
        this.liveMarketSyncService = liveMarketSyncService;
        this.volatility = volatility;
        this.circuitLimit = circuitLimit;
        this.priceFloor = Money.scale(priceFloor);
        this.retention = Duration.ofHours(retentionHours);
    }

    @Scheduled(fixedDelayString = "${tradesim.price-engine.interval-ms:1500}", initialDelay = 2000)
    public void tick() {
        List<Stock> stocks = stockRepository.findAll();
        Instant now = Instant.now();

        for (Stock stock : stocks) {
            BigDecimal nextPrice = computeNextPrice(stock);
            stockRepository.updatePrice(stock.symbol(), nextPrice);
            priceHistoryRepository.record(stock.symbol(), nextPrice, now);
            orderService.matchPendingOrdersForSymbol(stock.symbol(), nextPrice);
        }

        marketStreamService.broadcast(stockRepository.findAll());

        tickCounter++;
        if (tickCounter % 300 == 0) {
            priceHistoryRepository.deleteOlderThan(now.minus(retention));
        }
    }

    private BigDecimal computeNextPrice(Stock stock) {
        double current = stock.price().doubleValue();
        double shock = ThreadLocalRandom.current().nextGaussian() * volatility;
        shock = Math.max(-0.005, Math.min(0.005, shock));

        // Mean-revert gently toward real NSE anchor price when synced so after-hours micro-ticks stay realistic
        double reversion = liveMarketSyncService.getAnchorPrice(stock.symbol())
                .map(anchor -> ((anchor.doubleValue() - current) / Math.max(1.0, current)) * 0.18)
                .orElse(0.0);

        BigDecimal factor = BigDecimal.valueOf(1.0 + shock + reversion);
        BigDecimal candidate = stock.price().multiply(factor).setScale(2, RoundingMode.HALF_UP);

        BigDecimal lowerBand = stock.previousClose()
                .multiply(BigDecimal.ONE.subtract(circuitLimit))
                .setScale(2, RoundingMode.HALF_UP)
                .max(priceFloor);
        BigDecimal upperBand = stock.previousClose()
                .multiply(BigDecimal.ONE.add(circuitLimit))
                .setScale(2, RoundingMode.HALF_UP);

        if (candidate.compareTo(lowerBand) < 0) {
            return lowerBand;
        }
        if (candidate.compareTo(upperBand) > 0) {
            return upperBand;
        }
        return candidate;
    }
}
