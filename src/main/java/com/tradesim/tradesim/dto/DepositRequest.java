package com.tradesim.tradesim.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DepositRequest(
        @NotNull(message = "Deposit amount is required")
        @DecimalMin(value = "1.00", message = "Minimum deposit is 1.00")
        @DecimalMax(value = "10000000.00", message = "Maximum single deposit is 1,00,00,000.00")
        BigDecimal amount
) {
}

