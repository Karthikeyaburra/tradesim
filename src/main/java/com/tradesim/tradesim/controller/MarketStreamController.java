package com.tradesim.tradesim.controller;

import com.tradesim.tradesim.service.MarketStreamService;
import com.tradesim.tradesim.service.StockService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/stream")
public class MarketStreamController {

    private final MarketStreamService marketStreamService;
    private final StockService stockService;

    public MarketStreamController(MarketStreamService marketStreamService, StockService stockService) {
        this.marketStreamService = marketStreamService;
        this.stockService = stockService;
    }

    @GetMapping(value = "/market", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMarket() {
        return marketStreamService.subscribe(stockService.getAllStocks());
    }
}

