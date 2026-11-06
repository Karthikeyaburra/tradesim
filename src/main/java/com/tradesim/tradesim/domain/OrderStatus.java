package com.tradesim.tradesim.domain;

/**
 * Order lifecycle: PENDING -> FILLED or PENDING -> CANCELLED. Both transitions are
 * conditional SQL updates (WHERE status = 'PENDING'), so a fill and a cancel racing
 * on the same order can never both win.
 */
public enum OrderStatus {
    PENDING,
    FILLED,
    CANCELLED
}

