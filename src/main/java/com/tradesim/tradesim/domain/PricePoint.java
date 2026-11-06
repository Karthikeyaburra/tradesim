package com.tradesim.tradesim.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record PricePoint(BigDecimal price, Instant recordedAt) {
}

