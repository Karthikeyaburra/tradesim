package com.tradesim.tradesim.config;

import com.tradesim.tradesim.domain.Role;
import com.tradesim.tradesim.domain.Stock;
import com.tradesim.tradesim.repository.PriceHistoryRepository;
import com.tradesim.tradesim.repository.StockRepository;
import com.tradesim.tradesim.repository.UserRepository;
import com.tradesim.tradesim.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Random;

@Component
public class DataSeeder implements CommandLineRunner {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final JdbcTemplate jdbcTemplate;
    private final String adminUsername;
    private final String adminPassword;

    public DataSeeder(AuthService authService,
                      UserRepository userRepository,
                      StockRepository stockRepository,
                      PriceHistoryRepository priceHistoryRepository,
                      JdbcTemplate jdbcTemplate,
                      @Value("${tradesim.admin.username:admin}") String adminUsername,
                      @Value("${tradesim.admin.password:admin123}") String adminPassword) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.stockRepository = stockRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername(adminUsername)) {
            authService.createUser(adminUsername, adminPassword, Role.ADMIN);
        }

        // Safely retire demerged TATAMOTORS ticker if nobody holds shares or pending orders in it
        retireLegacyTickerIfUnheld("TATAMOTORS");

        List<Stock> defaults = List.of(
                new Stock("RELIANCE", "Reliance Industries", new BigDecimal("1313.40"), new BigDecimal("1311.00"), "Energy"),
                new Stock("TCS", "Tata Consultancy Services", new BigDecimal("2495.70"), new BigDecimal("2436.00"), "IT"),
                new Stock("HDFCBANK", "HDFC Bank", new BigDecimal("760.00"), new BigDecimal("736.70"), "Banking"),
                new Stock("INFY", "Infosys", new BigDecimal("1167.40"), new BigDecimal("1128.20"), "IT"),
                new Stock("ICICIBANK", "ICICI Bank", new BigDecimal("1500.00"), new BigDecimal("1424.10"), "Banking"),
                new Stock("BHARTIARTL", "Bharti Airtel", new BigDecimal("2056.90"), new BigDecimal("2067.60"), "Telecom"),
                new Stock("ITC", "ITC Limited", new BigDecimal("278.85"), new BigDecimal("280.00"), "FMCG"),
                new Stock("EICHERMOT", "Eicher Motors", new BigDecimal("7713.00"), new BigDecimal("7420.00"), "Auto"),
                new Stock("SBIN", "State Bank of India", new BigDecimal("1006.40"), new BigDecimal("979.00"), "Banking"),
                new Stock("BAJFINANCE", "Bajaj Finance", new BigDecimal("1070.00"), new BigDecimal("994.00"), "Finance"),
                new Stock("LT", "Larsen & Toubro", new BigDecimal("4050.00"), new BigDecimal("3944.40"), "Infra"),
                new Stock("SUNPHARMA", "Sun Pharmaceutical", new BigDecimal("1829.00"), new BigDecimal("1773.40"), "Pharma"),
                new Stock("MARUTI", "Maruti Suzuki India", new BigDecimal("12652.00"), new BigDecimal("11995.00"), "Auto"),
                new Stock("WIPRO", "Wipro Limited", new BigDecimal("198.40"), new BigDecimal("194.55"), "IT"),
                new Stock("TITAN", "Titan Company", new BigDecimal("4834.60"), new BigDecimal("4754.40"), "Consumer"),
                new Stock("NTPC", "NTPC Limited", new BigDecimal("316.70"), new BigDecimal("321.95"), "Energy")
        );

        Instant now = Instant.now();
        for (Stock stock : defaults) {
            if (!stockRepository.existsBySymbol(stock.symbol())) {
                stockRepository.save(stock);
            }
            if (priceHistoryRepository.findRecentBySymbol(stock.symbol(), 10).size() < 10) {
                seedIntradaySeries(stock, now);
            }
        }
    }

    private void retireLegacyTickerIfUnheld(String symbol) {
        Integer held = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM holdings WHERE symbol = ? AND quantity > 0", Integer.class, symbol);
        Integer pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE symbol = ? AND status = 'PENDING'", Integer.class, symbol);
        if ((held == null || held == 0) && (pending == null || pending == 0)) {
            stockRepository.deactivate(symbol);
        }
    }

    private void seedIntradaySeries(Stock stock, Instant now) {
        Random rng = new Random(stock.symbol().hashCode());
        int points = 72;
        double start = stock.previousClose().doubleValue();
        double target = stock.price().doubleValue();
        double current = start;

        for (int i = 0; i < points; i++) {
            Instant t = now.minusSeconds((long) (points - i) * 10L);
            if (i == points - 1) {
                current = target;
            } else {
                double progress = (double) i / (points - 1);
                double driftToTarget = (target - current) * (0.06 + 0.08 * progress);
                double shock = (rng.nextGaussian() * 0.0035) * start;
                current = Math.max(1.0, current + driftToTarget + shock);
            }
            BigDecimal p = BigDecimal.valueOf(current).setScale(2, RoundingMode.HALF_UP);
            priceHistoryRepository.record(stock.symbol(), p, t);
        }
    }
}
