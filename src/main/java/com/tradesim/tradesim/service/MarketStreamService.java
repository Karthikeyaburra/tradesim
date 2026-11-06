package com.tradesim.tradesim.service;

import com.tradesim.tradesim.domain.Stock;
import com.tradesim.tradesim.dto.StockResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Push-based market data broadcaster using Server-Sent Events (SSE).
 * Eliminates client polling lag by pushing every PriceEngine tick to all connected terminals.
 */
@Service
public class MarketStreamService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(List<Stock> initialSnapshot) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        try {
            List<StockResponse> payload = initialSnapshot.stream().map(StockResponse::from).toList();
            emitter.send(SseEmitter.event()
                    .name("quotes")
                    .data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void broadcast(List<Stock> stocks) {
        if (emitters.isEmpty()) {
            return;
        }
        List<StockResponse> payload = stocks.stream().map(StockResponse::from).toList();
        List<SseEmitter> dead = new ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("quotes")
                        .data(payload, MediaType.APPLICATION_JSON));
            } catch (Exception ex) {
                dead.add(emitter);
            }
        }
        if (!dead.isEmpty()) {
            emitters.removeAll(dead);
        }
    }
}

