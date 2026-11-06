package com.tradesim.tradesim.exception;

public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(Long userId) {
        super("User not found: " + userId);
    }
}

