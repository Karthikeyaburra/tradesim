package com.tradesim.tradesim.domain;

import java.time.Instant;

public record Session(
        String token,
        Long userId,
        Instant createdAt,
        Instant expiresAt
) {
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}

