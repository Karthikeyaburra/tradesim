package com.tradesim.tradesim.dto;

import com.tradesim.tradesim.domain.Role;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminUserSummary(
        Long id,
        String username,
        Role role,
        Instant createdAt,
        BigDecimal cash,
        BigDecimal holdingsValue,
        BigDecimal totalValue,
        Boolean ledgerConsistent
) {
}

