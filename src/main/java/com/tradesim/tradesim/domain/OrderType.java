package com.tradesim.tradesim.domain;

public enum OrderType {
    /** Executes immediately at the current server price. */
    MARKET,
    /** Rests as PENDING (with cash or shares reserved) until the price crosses the limit. */
    LIMIT
}

