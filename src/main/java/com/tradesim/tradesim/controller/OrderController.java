package com.tradesim.tradesim.controller;

import com.tradesim.tradesim.domain.OrderFilter;
import com.tradesim.tradesim.domain.OrderSide;
import com.tradesim.tradesim.domain.OrderStatus;
import com.tradesim.tradesim.domain.OrderType;
import com.tradesim.tradesim.domain.Role;
import com.tradesim.tradesim.domain.User;
import com.tradesim.tradesim.dto.OrderResponse;
import com.tradesim.tradesim.dto.PlaceOrderRequest;
import com.tradesim.tradesim.security.CurrentUser;
import com.tradesim.tradesim.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final ZoneId zoneId;

    public OrderController(OrderService orderService,
                           @Value("${tradesim.zone:Asia/Kolkata}") String zoneName) {
        this.orderService = orderService;
        this.zoneId = ZoneId.of(zoneName);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse placeOrder(HttpServletRequest request,
                                    @Valid @RequestBody PlaceOrderRequest body) {
        User trader = CurrentUser.requireRole(request, Role.TRADER);
        return OrderResponse.from(orderService.placeOrder(trader.id(), body));
    }

    @GetMapping
    public List<OrderResponse> listOrders(
            HttpServletRequest request,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) OrderSide side,
            @RequestParam(required = false) OrderType type,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        User trader = CurrentUser.requireRole(request, Role.TRADER);
        OrderFilter filter = toFilter(symbol, side, type, status, from, to);
        return orderService.getOrders(trader.id(), filter).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancelOrder(HttpServletRequest request, @PathVariable Long id) {
        User trader = CurrentUser.requireRole(request, Role.TRADER);
        return OrderResponse.from(orderService.cancelOrder(trader.id(), id));
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(
            HttpServletRequest request,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) OrderSide side,
            @RequestParam(required = false) OrderType type,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        User trader = CurrentUser.requireRole(request, Role.TRADER);
        OrderFilter filter = toFilter(symbol, side, type, status, from, to);
        String csv = orderService.exportOrdersCsv(trader.id(), filter);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tradesim-orders.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private OrderFilter toFilter(String symbol, OrderSide side, OrderType type, OrderStatus status,
                                 LocalDate from, LocalDate to) {
        Instant fromInstant = from != null ? from.atStartOfDay(zoneId).toInstant() : null;
        Instant toExclusive = to != null ? to.plusDays(1).atStartOfDay(zoneId).toInstant() : null;
        return new OrderFilter(symbol, side, type, status, fromInstant, toExclusive);
    }
}

