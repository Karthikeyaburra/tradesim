package com.tradesim.tradesim.controller;

import com.tradesim.tradesim.domain.PricePoint;
import com.tradesim.tradesim.dto.StockResponse;
import com.tradesim.tradesim.service.StockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    public List<StockResponse> getAllStocks() {
        return stockService.getAllStocks().stream()
                .map(StockResponse::from)
                .toList();
    }

    @GetMapping("/{symbol}")
    public StockResponse getBySymbol(@PathVariable String symbol) {
        return StockResponse.from(stockService.getBySymbol(symbol));
    }

    @GetMapping("/{symbol}/history")
    public List<PricePoint> getHistory(@PathVariable String symbol,
                                       @RequestParam(defaultValue = "120") int limit) {
        return stockService.getHistory(symbol, limit);
    }
}
