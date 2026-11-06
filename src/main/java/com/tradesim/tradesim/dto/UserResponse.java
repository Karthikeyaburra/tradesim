package com.tradesim.tradesim.dto;

import com.tradesim.tradesim.domain.Role;
import com.tradesim.tradesim.domain.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        Role role,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.username(), user.role(), user.createdAt());
    }
}

