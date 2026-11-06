package com.tradesim.tradesim.service.order;

import com.tradesim.tradesim.domain.Money;
import com.tradesim.tradesim.domain.Order;
import com.tradesim.tradesim.domain.OrderSide;
import com.tradesim.tradesim.domain.OrderStatus;
import com.tradesim.tradesim.domain.OrderType;
import com.tradesim.tradesim.domain.Stock;
import com.tradesim.tradesim.dto.PlaceOrderRequest;
import com.tradesim.tradesim.exception.InsufficientSharesException;
import com.tradesim.tradesim.exception.InvalidRequestException;
import com.tradesim.tradesim.repository.HoldingRepository;
import com.tradesim.tradesim.repository.OrderRepository;
import com.tradesim.tradesim.service.WalletService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class LimitOrderStrategy implements OrderExecutionStrategy {

    private final WalletService walletService;
    private final HoldingRepository holdingRepository;
    private final OrderRepository orderRepository;
    private final PendingOrderSettlement settlement;

    public LimitOrderStrategy(WalletService walletService,
                              HoldingRepository holdingRepository,
                              OrderRepository orderRepository,
                              PendingOrderSettlement settlement) {
        this.walletService = walletService;
        this.holdingRepository = holdingRepository;
        this.orderRepository = orderRepository;
        this.settlement = settlement;
    }

    @Override
    public OrderType supports() {
        return OrderType.LIMIT;
    }

    @Override
    public Order execute(Long userId, Stock stock, PlaceOrderRequest request, Instant now) {
        if (request.limitPrice() == null || request.limitPrice().compareTo(Money.of("0.01")) < 0) {
            throw new InvalidRequestException("Limit orders require a positive limitPrice");
        }

        int qty = request.quantity();
        BigDecimal limitPrice = Money.scale(request.limitPrice());

        if (request.side() == OrderSide.BUY) {
            BigDecimal requiredReserve = Money.times(limitPrice, qty);
            walletService.reserveOrThrow(userId, requiredReserve, now);
        } else {
            if (!holdingRepository.reserveShares(userId, stock.symbol(), qty)) {
                int available = holdingRepository.findRaw(userId, stock.symbol())
                        .map(HoldingRepository.RawHolding::availableQuantity)
                        .orElse(0);
                throw new InsufficientSharesException(stock.symbol(), qty, available);
            }
        }

        Order pending = orderRepository.save(new Order(
                null, userId, stock.symbol(), request.side(), OrderType.LIMIT,
                qty, limitPrice, null, OrderStatus.PENDING, now, now
        ));

        // If already marketable against the live price, fill immediately with price improvement.
        boolean marketableBuy = request.side() == OrderSide.BUY && limitPrice.compareTo(stock.price()) >= 0;
        boolean marketableSell = request.side() == OrderSide.SELL && limitPrice.compareTo(stock.price()) <= 0;
        if (marketableBuy || marketableSell) {
            return settlement.fillIfStillPending(pending, stock.price(), now).orElse(pending);
        }
        return pending;
    }
}

