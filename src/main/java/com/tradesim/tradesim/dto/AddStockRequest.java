package com.tradesim.tradesim.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AddStockRequest(
        @NotBlank(message = "Symbol is required")
        @Size(min = 1, max = 16, message = "Symbol must be 1 to 16 characters")
        @Pattern(regexp = "^[a-zA-Z0-9&]+$", message = "Symbol may only contain letters, digits, and '&'")
        String symbol,

        @NotBlank(message = "Company name is required")
        @Size(max = 100, message = "Company name must be at most 100 characters")
        String name,

        @NotBlank(message = "Sector is required")
        @Size(max = 50, message = "Sector must be at most 50 characters")
        String sector,

        @NotNull(message = "Initial price is required")
        @DecimalMin(value = "1.00", message = "Initial price must be at least 1.00")
        BigDecimal price
) {
}

