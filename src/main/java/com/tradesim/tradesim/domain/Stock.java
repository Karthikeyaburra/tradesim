package com.tradesim.tradesim.domain;

import java.math.BigDecimal;

public record Stock(
        String symbol,
        String name,
        BigDecimal price,
        BigDecimal previousClose,
        String sector
) {
}
