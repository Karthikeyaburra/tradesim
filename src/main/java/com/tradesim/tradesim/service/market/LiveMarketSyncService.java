package com.tradesim.tradesim.service.market;

import com.tradesim.tradesim.domain.Stock;
import com.tradesim.tradesim.repository.PriceHistoryRepository;
import com.tradesim.tradesim.repository.StockRepository;
import com.tradesim.tradesim.service.MarketStreamService;
import com.tradesim.tradesim.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically anchors listed stocks to real NSE market prices and 5-minute intraday candles
 * via {@link MarketDataProvider}, while exposing live anchor prices so {@link com.tradesim.tradesim.service.PriceEngine}
 * can interpolate realistic 1.5s micro-ticks 24/7 (including nights and weekends).
 */
@Service
public class LiveMarketSyncService {

    private static final Logger log = LoggerFactory.getLogger(LiveMarketSyncService.class);

    private final MarketDataProvider marketDataProvider;
    private final StockRepository stockRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final OrderService orderService;
    private final MarketStreamService marketStreamService;
    private final boolean syncEnabled;

    private final Map<String, BigDecimal> realAnchorPrices = new ConcurrentHashMap<>();
    private final Set<String> historyBootstrapped = ConcurrentHashMap.newKeySet();

    public LiveMarketSyncService(MarketDataProvider marketDataProvider,
                                 StockRepository stockRepository,
                                 PriceHistoryRepository priceHistoryRepository,
                                 OrderService orderService,
                                 MarketStreamService marketStreamService,
                                 @org.springframework.beans.factory.annotation.Value("${tradesim.market-sync.enabled:true}") boolean syncEnabled) {
        this.marketDataProvider = marketDataProvider;
        this.stockRepository = stockRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.orderService = orderService;
        this.marketStreamService = marketStreamService;
        this.syncEnabled = syncEnabled;
    }

    public Optional<BigDecimal> getAnchorPrice(String symbol) {
        return Optional.ofNullable(realAnchorPrices.get(symbol));
    }

    @Scheduled(
            initialDelayString = "${tradesim.market-sync.initial-delay-ms:400}",
            fixedDelayString = "${tradesim.market-sync.interval-ms:60000}"
    )
    @ConditionalOnProperty(name = "tradesim.market-sync.enabled", havingValue = "true", matchIfMissing = true)
    public void syncFromExchange() {
        if (!syncEnabled) {
            return;
        }
        List<Stock> listed = stockRepository.findAll();
        int synced = 0;

        for (Stock stock : listed) {
            Optional<ExternalQuote> quoteOpt = marketDataProvider.fetchQuote(stock.symbol());
            if (quoteOpt.isEmpty()) {
                continue;
            }
            ExternalQuote quote = quoteOpt.get();
            realAnchorPrices.put(stock.symbol(), quote.price());
            stockRepository.updateQuote(stock.symbol(), quote.price(), quote.previousClose());

            if (historyBootstrapped.add(stock.symbol()) && quote.intradayHistory().size() >= 8) {
                priceHistoryRepository.replaceSeries(stock.symbol(), quote.intradayHistory());
            }

            orderService.matchPendingOrdersForSymbol(stock.symbol(), quote.price());
            synced++;
        }

        if (synced > 0) {
            marketStreamService.broadcast(stockRepository.findAll());
            log.info("Synced live NSE market data for {}/{} listed equities", synced, listed.size());
        }
    }
}

