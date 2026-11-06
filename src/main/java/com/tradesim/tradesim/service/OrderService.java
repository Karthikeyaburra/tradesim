package com.tradesim.tradesim.service;

import com.tradesim.tradesim.domain.Order;
import com.tradesim.tradesim.domain.OrderFilter;
import com.tradesim.tradesim.domain.OrderType;
import com.tradesim.tradesim.domain.Stock;
import com.tradesim.tradesim.dto.PlaceOrderRequest;
import com.tradesim.tradesim.exception.OrderNotFoundException;
import com.tradesim.tradesim.exception.OrderNotPendingException;
import com.tradesim.tradesim.repository.OrderRepository;
import com.tradesim.tradesim.service.order.OrderExecutionStrategy;
import com.tradesim.tradesim.service.order.PendingOrderSettlement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private final StockService stockService;
    private final OrderRepository orderRepository;
    private final PendingOrderSettlement settlement;
    private final Map<OrderType, OrderExecutionStrategy> strategies;

    public OrderService(StockService stockService,
                        OrderRepository orderRepository,
                        PendingOrderSettlement settlement,
                        List<OrderExecutionStrategy> strategyBeans) {
        this.stockService = stockService;
        this.orderRepository = orderRepository;
        this.settlement = settlement;
        this.strategies = new EnumMap<>(OrderType.class);
        for (OrderExecutionStrategy s : strategyBeans) {
            this.strategies.put(s.supports(), s);
        }
    }

    @Transactional
    public Order placeOrder(Long userId, PlaceOrderRequest request) {
        Stock stock = stockService.getBySymbol(request.symbol());
        OrderExecutionStrategy strategy = strategies.get(request.type());
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported order type: " + request.type());
        }
        return strategy.execute(userId, stock, request, Instant.now());
    }

    @Transactional
    public Order cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.userId().equals(userId))
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        Instant now = Instant.now();
        if (!settlement.cancelIfStillPending(order, now)) {
            throw new OrderNotPendingException(orderId);
        }
        return order.cancelled(now);
    }

    /** Invoked when a stock ticks to a new price to fill all crossed pending limit orders. */
    public int matchPendingOrdersForSymbol(String symbol, BigDecimal currentPrice) {
        List<Order> candidates = orderRepository.findMatchablePendingOrders(symbol, currentPrice);
        int filledCount = 0;
        Instant now = Instant.now();
        for (Order candidate : candidates) {
            if (settlement.fillIfStillPending(candidate, currentPrice, now).isPresent()) {
                filledCount++;
            }
        }
        return filledCount;
    }

    public List<Order> getOrders(Long userId, OrderFilter filter) {
        return orderRepository.findByUser(userId, filter);
    }

    public String exportOrdersCsv(Long userId, OrderFilter filter) {
        List<Order> orders = getOrders(userId, filter);
        StringBuilder csv = new StringBuilder();
        csv.append("order_id,created_at,symbol,side,type,quantity,limit_price,executed_price,total_value,status\n");
        for (Order o : orders) {
            csv.append(o.id()).append(',')
                    .append(o.createdAt()).append(',')
                    .append(escapeCsv(o.symbol())).append(',')
                    .append(o.side()).append(',')
                    .append(o.type()).append(',')
                    .append(o.quantity()).append(',')
                    .append(o.limitPrice() != null ? o.limitPrice().toPlainString() : "").append(',')
                    .append(o.executedPrice() != null ? o.executedPrice().toPlainString() : "").append(',')
                    .append(o.totalValue() != null ? o.totalValue().toPlainString() : "").append(',')
                    .append(o.status()).append('\n');
        }
        return csv.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

