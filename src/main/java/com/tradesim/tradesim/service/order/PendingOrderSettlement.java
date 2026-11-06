package com.tradesim.tradesim.service.order;

import com.tradesim.tradesim.domain.LedgerType;
import com.tradesim.tradesim.domain.Money;
import com.tradesim.tradesim.domain.Order;
import com.tradesim.tradesim.domain.OrderSide;
import com.tradesim.tradesim.repository.HoldingRepository;
import com.tradesim.tradesim.repository.OrderRepository;
import com.tradesim.tradesim.service.WalletService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Atomically transitions a PENDING limit order to either FILLED or CANCELLED.
 * Both methods gate on `WHERE id = ? AND status = 'PENDING'`, making double-fills
 * and double-refunds impossible even when user cancellations race with price ticks.
 */
@Component
public class PendingOrderSettlement {

    private final OrderRepository orderRepository;
    private final WalletService walletService;
    private final HoldingRepository holdingRepository;

    public PendingOrderSettlement(OrderRepository orderRepository,
                                  WalletService walletService,
                                  HoldingRepository holdingRepository) {
        this.orderRepository = orderRepository;
        this.walletService = walletService;
        this.holdingRepository = holdingRepository;
    }

    @Transactional
    public Optional<Order> fillIfStillPending(Order order, BigDecimal executionPrice, Instant now) {
        BigDecimal execPrice = Money.scale(executionPrice);
        if (!orderRepository.markFilledIfPending(order.id(), execPrice, now)) {
            return Optional.empty();
        }

        BigDecimal fillValue = Money.times(execPrice, order.quantity());
        if (order.side() == OrderSide.BUY) {
            BigDecimal reserved = order.reservedCash();
            walletService.settleReservedBuy(
                    order.userId(), reserved, fillValue, order.id(),
                    "LIMIT BUY " + order.quantity() + " " + order.symbol() + " @ ₹" + execPrice.toPlainString(), now
            );
            holdingRepository.addShares(order.userId(), order.symbol(), order.quantity(), execPrice);
        } else {
            holdingRepository.consumeReservedShares(order.userId(), order.symbol(), order.quantity());
            walletService.creditProceeds(
                    order.userId(), fillValue, LedgerType.TRADE_SELL, order.id(),
                    "LIMIT SELL " + order.quantity() + " " + order.symbol() + " @ ₹" + execPrice.toPlainString(), now
            );
        }
        return Optional.of(order.filled(execPrice, now));
    }

    @Transactional
    public boolean cancelIfStillPending(Order order, Instant now) {
        if (!orderRepository.markCancelledIfPending(order.id(), now)) {
            return false;
        }
        if (order.side() == OrderSide.BUY) {
            walletService.cancelReservedBuy(order.userId(), order.reservedCash(), now);
        } else {
            holdingRepository.releaseReservedShares(order.userId(), order.symbol(), order.quantity());
        }
        return true;
    }
}

