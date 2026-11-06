package com.tradesim.tradesim.service;

import com.tradesim.tradesim.domain.LedgerType;
import com.tradesim.tradesim.domain.Money;
import com.tradesim.tradesim.domain.Order;
import com.tradesim.tradesim.domain.Position;
import com.tradesim.tradesim.domain.Role;
import com.tradesim.tradesim.domain.Stock;
import com.tradesim.tradesim.domain.User;
import com.tradesim.tradesim.domain.Wallet;
import com.tradesim.tradesim.dto.AddStockRequest;
import com.tradesim.tradesim.dto.AdminUserSummary;
import com.tradesim.tradesim.dto.WalletResponse;
import com.tradesim.tradesim.exception.InvalidRequestException;
import com.tradesim.tradesim.exception.StockNotFoundException;
import com.tradesim.tradesim.exception.UserNotFoundException;
import com.tradesim.tradesim.repository.HoldingRepository;
import com.tradesim.tradesim.repository.OrderRepository;
import com.tradesim.tradesim.repository.StockRepository;
import com.tradesim.tradesim.repository.UserRepository;
import com.tradesim.tradesim.service.order.PendingOrderSettlement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final HoldingRepository holdingRepository;
    private final OrderRepository orderRepository;
    private final StockRepository stockRepository;
    private final StockService stockService;
    private final PendingOrderSettlement settlement;

    public AdminService(UserRepository userRepository,
                        WalletService walletService,
                        HoldingRepository holdingRepository,
                        OrderRepository orderRepository,
                        StockRepository stockRepository,
                        StockService stockService,
                        PendingOrderSettlement settlement) {
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.holdingRepository = holdingRepository;
        this.orderRepository = orderRepository;
        this.stockRepository = stockRepository;
        this.stockService = stockService;
        this.settlement = settlement;
    }

    public List<AdminUserSummary> listUsers() {
        List<User> users = userRepository.findAll();
        List<AdminUserSummary> result = new ArrayList<>(users.size());
        for (User u : users) {
            if (u.role() == Role.ADMIN) {
                result.add(new AdminUserSummary(u.id(), u.username(), u.role(), u.createdAt(), null, null, null, null));
            } else {
                Wallet w = walletService.getWallet(u.id());
                BigDecimal holdingsVal = Money.ZERO;
                for (Position p : holdingRepository.findPositionsByUserId(u.id())) {
                    holdingsVal = holdingsVal.add(p.marketValue());
                }
                BigDecimal total = w.totalCash().add(holdingsVal);
                boolean ok = walletService.isLedgerConsistent(u.id());
                result.add(new AdminUserSummary(
                        u.id(), u.username(), u.role(), u.createdAt(),
                        w.totalCash(), holdingsVal, total, ok
                ));
            }
        }
        return result;
    }

    @Transactional
    public WalletResponse depositToTrader(Long userId, BigDecimal amount) {
        User target = requireTrader(userId);
        return walletService.adminDeposit(target.id(), amount);
    }

    @Transactional
    public WalletResponse resetTraderAccount(Long userId) {
        User target = requireTrader(userId);
        Instant now = Instant.now();

        for (Order pending : orderRepository.findPendingByUserId(target.id())) {
            settlement.cancelIfStillPending(pending, now);
        }
        holdingRepository.deleteByUserId(target.id());
        return walletService.resetToInitialBalance(target.id(), now);
    }

    @Transactional
    public Stock addStock(AddStockRequest request) {
        return stockService.addOrRelistStock(request);
    }

    /**
     * Delists a stock cleanly: cancels all resting PENDING limit orders on that symbol,
     * settles any open trader holdings in cash at the final price, and marks the stock inactive.
     */
    @Transactional
    public void delistStock(String rawSymbol) {
        Stock stock = stockService.getBySymbol(rawSymbol);
        Instant now = Instant.now();

        for (Order pending : orderRepository.findPendingBySymbol(stock.symbol())) {
            settlement.cancelIfStillPending(pending, now);
        }

        for (HoldingRepository.RawHolding holder : holdingRepository.findAllHoldersOfSymbol(stock.symbol())) {
            BigDecimal payout = Money.times(stock.price(), holder.quantity());
            walletService.creditProceeds(
                    holder.userId(),
                    payout,
                    LedgerType.DELISTING_SETTLEMENT,
                    null,
                    "Delisting cash settlement: " + holder.quantity() + " " + stock.symbol() + " @ ₹" + stock.price().toPlainString(),
                    now
            );
        }
        holdingRepository.deleteBySymbol(stock.symbol());

        if (!stockRepository.deactivate(stock.symbol())) {
            throw new StockNotFoundException(stock.symbol());
        }
    }

    private User requireTrader(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (user.role() != Role.TRADER) {
            throw new InvalidRequestException("Admin accounts do not have trading wallets");
        }
        return user;
    }
}

