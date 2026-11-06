package com.tradesim.tradesim.exception;

public class DuplicateUsernameException extends ConflictException {

    public DuplicateUsernameException(String username) {
        super("Username already taken: " + username);
    }
}
