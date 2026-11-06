package com.tradesim.tradesim.service.order;

import com.tradesim.tradesim.domain.LedgerEntry;
import com.tradesim.tradesim.domain.LedgerType;
import com.tradesim.tradesim.domain.Money;
import com.tradesim.tradesim.domain.Order;
import com.tradesim.tradesim.domain.OrderSide;
import com.tradesim.tradesim.domain.OrderStatus;
import com.tradesim.tradesim.domain.OrderType;
import com.tradesim.tradesim.domain.Stock;
import com.tradesim.tradesim.domain.Wallet;
import com.tradesim.tradesim.dto.PlaceOrderRequest;
import com.tradesim.tradesim.exception.InsufficientFundsException;
import com.tradesim.tradesim.exception.InsufficientSharesException;
import com.tradesim.tradesim.exception.InvalidRequestException;
import com.tradesim.tradesim.repository.HoldingRepository;
import com.tradesim.tradesim.repository.LedgerRepository;
import com.tradesim.tradesim.repository.OrderRepository;
import com.tradesim.tradesim.repository.WalletRepository;
import com.tradesim.tradesim.service.WalletService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class MarketOrderStrategy implements OrderExecutionStrategy {

    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final HoldingRepository holdingRepository;
    private final OrderRepository orderRepository;
    private final LedgerRepository ledgerRepository;

    public MarketOrderStrategy(WalletRepository walletRepository,
                               WalletService walletService,
                               HoldingRepository holdingRepository,
                               OrderRepository orderRepository,
                               LedgerRepository ledgerRepository) {
        this.walletRepository = walletRepository;
        this.walletService = walletService;
        this.holdingRepository = holdingRepository;
        this.orderRepository = orderRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Override
    public OrderType supports() {
        return OrderType.MARKET;
    }

    @Override
    public Order execute(Long userId, Stock stock, PlaceOrderRequest request, Instant now) {
        if (request.limitPrice() != null) {
            throw new InvalidRequestException("Market orders must not specify a limitPrice");
        }

        int qty = request.quantity();
        BigDecimal execPrice = Money.scale(stock.price());
        BigDecimal totalValue = Money.times(execPrice, qty);

        if (request.side() == OrderSide.BUY) {
            if (!walletRepository.debitCash(userId, totalValue, now)) {
                BigDecimal available = walletRepository.findByUserId(userId)
                        .map(Wallet::balance)
                        .orElse(Money.ZERO);
                throw new InsufficientFundsException(totalValue, available);
            }

            Order saved = orderRepository.save(new Order(
                    null, userId, stock.symbol(), OrderSide.BUY, OrderType.MARKET,
                    qty, null, execPrice, OrderStatus.FILLED, now, now
            ));

            holdingRepository.addShares(userId, stock.symbol(), qty, execPrice);

            Wallet updatedWallet = walletService.getWallet(userId);
            ledgerRepository.save(new LedgerEntry(
                    null, userId, LedgerType.TRADE_BUY, totalValue.negate(), updatedWallet.totalCash(),
                    saved.id(), "MARKET BUY " + qty + " " + stock.symbol() + " @ ₹" + execPrice.toPlainString(), now
            ));
            return saved;
        } else {
            if (!holdingRepository.removeUnreservedShares(userId, stock.symbol(), qty)) {
                int available = holdingRepository.findRaw(userId, stock.symbol())
                        .map(HoldingRepository.RawHolding::availableQuantity)
                        .orElse(0);
                throw new InsufficientSharesException(stock.symbol(), qty, available);
            }

            Order saved = orderRepository.save(new Order(
                    null, userId, stock.symbol(), OrderSide.SELL, OrderType.MARKET,
                    qty, null, execPrice, OrderStatus.FILLED, now, now
            ));

            walletService.creditProceeds(
                    userId, totalValue, LedgerType.TRADE_SELL, saved.id(),
                    "MARKET SELL " + qty + " " + stock.symbol() + " @ ₹" + execPrice.toPlainString(), now
            );
            return saved;
        }
    }
}

