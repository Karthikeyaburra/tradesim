package com.tradesim.tradesim;

import com.tradesim.tradesim.domain.Money;
import com.tradesim.tradesim.domain.Order;
import com.tradesim.tradesim.domain.OrderFilter;
import com.tradesim.tradesim.domain.OrderSide;
import com.tradesim.tradesim.domain.OrderStatus;
import com.tradesim.tradesim.domain.OrderType;
import com.tradesim.tradesim.domain.User;
import com.tradesim.tradesim.domain.Wallet;
import com.tradesim.tradesim.dto.AddStockRequest;
import com.tradesim.tradesim.dto.LeaderboardEntry;
import com.tradesim.tradesim.dto.PlaceOrderRequest;
import com.tradesim.tradesim.dto.PortfolioResponse;
import com.tradesim.tradesim.dto.RegisterRequest;
import com.tradesim.tradesim.exception.InsufficientFundsException;
import com.tradesim.tradesim.exception.OrderNotPendingException;
import com.tradesim.tradesim.service.AdminService;
import com.tradesim.tradesim.service.AuthService;
import com.tradesim.tradesim.service.LeaderboardService;
import com.tradesim.tradesim.service.OrderService;
import com.tradesim.tradesim.service.PortfolioService;
import com.tradesim.tradesim.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TradeSimConcurrencyAndFlowTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTestTrades() {
        jdbcTemplate.update("DELETE FROM ledger_entries");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM holdings");
        jdbcTemplate.update("DELETE FROM wallets");
        jdbcTemplate.update("DELETE FROM sessions");
        jdbcTemplate.update("DELETE FROM users WHERE role = 'TRADER'");
    }

    @Test
    void concurrentMarketBuys_50ThreadsRacingFor10AffordableOrders_zeroOverdraftAndExactLedger() throws Exception {
        adminService.addStock(new AddStockRequest("RACE", "Race Corp", "Tech", new BigDecimal("100000.00")));
        User trader = registerUniqueTrader("racer");

        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger filled = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        AtomicInteger unexpectedErrors = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    startGun.await();
                    Order order = orderService.placeOrder(
                            trader.id(),
                            new PlaceOrderRequest("RACE", OrderSide.BUY, OrderType.MARKET, 1, null)
                    );
                    if (order.status() == OrderStatus.FILLED) {
                        filled.incrementAndGet();
                    }
                } catch (InsufficientFundsException ex) {
                    rejected.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        startGun.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(0, unexpectedErrors.get(), "No deadlocks or unexpected exceptions allowed");
        assertEquals(10, filled.get(), "Exactly 10 orders of ₹1,00,000 should fill from ₹10,00,000");
        assertEquals(40, rejected.get(), "Remaining 40 racing orders must be cleanly rejected");

        Wallet wallet = walletService.getWallet(trader.id());
        assertEquals(0, wallet.balance().compareTo(Money.ZERO), "Final cash balance must be ₹0.00");
        assertTrue(walletService.isLedgerConsistent(trader.id()), "Ledger invariant must hold after 50-thread race");

        PortfolioResponse portfolio = portfolioService.getPortfolio(trader.id());
        assertEquals(1, portfolio.holdings().size());
        assertEquals(10, portfolio.holdings().get(0).quantity());
    }

    @Test
    void concurrentLimitCancelVsMatch_20ThreadsRacing_exactlyOneOutcomeWins() throws Exception {
        adminService.addStock(new AddStockRequest("LIMITX", "Limit Test Ltd", "IT", new BigDecimal("500.00")));
        User trader = registerUniqueTrader("canceller");

        Order pending = orderService.placeOrder(
                trader.id(),
                new PlaceOrderRequest("LIMITX", OrderSide.BUY, OrderType.LIMIT, 10, new BigDecimal("450.00"))
        );
        assertEquals(OrderStatus.PENDING, pending.status());

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger cancelWins = new AtomicInteger(0);
        AtomicInteger matchWins = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final boolean isCanceller = (i % 2 == 0);
            pool.submit(() -> {
                ready.countDown();
                try {
                    startGun.await();
                    if (isCanceller) {
                        orderService.cancelOrder(trader.id(), pending.id());
                        cancelWins.incrementAndGet();
                    } else {
                        int matched = orderService.matchPendingOrdersForSymbol("LIMITX", new BigDecimal("440.00"));
                        matchWins.addAndGet(matched);
                    }
                } catch (OrderNotPendingException | InterruptedException ignored) {
                    // Expected for losing canceller threads
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        startGun.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(1, cancelWins.get() + matchWins.get(), "Exactly ONE transition (FILL XOR CANCEL) may win");
        Wallet wallet = walletService.getWallet(trader.id());
        assertEquals(0, wallet.reservedCash().compareTo(Money.ZERO), "All reserved cash must be settled or released");
        assertTrue(walletService.isLedgerConsistent(trader.id()), "Ledger invariant must hold after cancel-vs-fill race");
    }

    @Test
    void fullTradingLifecycle_avgPrice_priceImprovement_leaderboard_csv_adminResetAndDelist() {
        adminService.addStock(new AddStockRequest("ALPHA", "Alpha Systems", "IT", new BigDecimal("1000.00")));
        User traderA = registerUniqueTrader("alpha_t");
        User traderB = registerUniqueTrader("beta_t");

        // 1. Buy 10 @ 1000, then price moves to 1200 and buy 10 @ 1200 -> weighted avg = 1100.00
        orderService.placeOrder(traderA.id(), new PlaceOrderRequest("ALPHA", OrderSide.BUY, OrderType.MARKET, 10, null));
        adminService.addStock(new AddStockRequest("ALPHA", "Alpha Systems", "IT", new BigDecimal("1200.00")));
        orderService.placeOrder(traderA.id(), new PlaceOrderRequest("ALPHA", OrderSide.BUY, OrderType.MARKET, 10, null));

        PortfolioResponse p1 = portfolioService.getPortfolio(traderA.id());
        assertEquals(0, p1.holdings().get(0).avgBuyPrice().compareTo(new BigDecimal("1100.00")));
        assertEquals(20, p1.holdings().get(0).quantity());

        // 2. Price rises to 1500 -> Trader A has unrealized profit: 20 * (1500 - 1100) = +8,000 -> net worth 10,08,000
        jdbcTemplate.update("UPDATE stocks SET price = 1500.00 WHERE symbol = 'ALPHA'");
        List<LeaderboardEntry> board = leaderboardService.getLeaderboard();
        assertEquals(traderA.username(), board.get(0).username());
        assertEquals(0, board.get(0).totalValue().compareTo(new BigDecimal("1008000.00")));
        assertEquals(2, board.size());
        assertEquals(traderB.username(), board.get(1).username());

        // 3. Place resting Limit SELL of 5 @ 1600 -> locks 5 shares, 15 available
        Order limitSell = orderService.placeOrder(
                traderA.id(),
                new PlaceOrderRequest("ALPHA", OrderSide.SELL, OrderType.LIMIT, 5, new BigDecimal("1600.00"))
        );
        assertEquals(OrderStatus.PENDING, limitSell.status());
        assertEquals(15, portfolioService.getPortfolio(traderA.id()).holdings().get(0).availableQuantity());

        // 4. Price ticks to 1650 -> Limit SELL fills at 1650 (price improvement!)
        int matched = orderService.matchPendingOrdersForSymbol("ALPHA", new BigDecimal("1650.00"));
        assertEquals(1, matched);
        assertTrue(walletService.isLedgerConsistent(traderA.id()));

        // 5. CSV export includes header + 3 orders
        String csv = orderService.exportOrdersCsv(traderA.id(), new OrderFilter("ALPHA", null, null, null, null, null));
        assertTrue(csv.startsWith("order_id,created_at,symbol,side,type,quantity"));
        assertEquals(4, csv.trim().split("\n").length);

        // 6. Admin deposit + delist settlement + reset -> ledger stays 100% consistent throughout
        adminService.depositToTrader(traderA.id(), new BigDecimal("25000.00"));
        assertTrue(walletService.isLedgerConsistent(traderA.id()));

        adminService.delistStock("ALPHA");
        assertTrue(walletService.isLedgerConsistent(traderA.id()));
        assertTrue(portfolioService.getPortfolio(traderA.id()).holdings().isEmpty());

        adminService.resetTraderAccount(traderA.id());
        assertTrue(walletService.isLedgerConsistent(traderA.id()));
        assertEquals(0, walletService.getWallet(traderA.id()).balance().compareTo(new BigDecimal("1000000.00")));
    }

    private User registerUniqueTrader(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return authService.register(new RegisterRequest(prefix + "_" + suffix, "password123"));
    }
}
