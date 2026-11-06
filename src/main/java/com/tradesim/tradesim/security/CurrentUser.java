package com.tradesim.tradesim.security;

import com.tradesim.tradesim.domain.Role;
import com.tradesim.tradesim.domain.User;
import com.tradesim.tradesim.exception.ForbiddenException;
import com.tradesim.tradesim.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static User get(HttpServletRequest request) {
        Object user = request.getAttribute(AuthInterceptor.USER_ATTRIBUTE);
        if (user == null) {
            throw new UnauthorizedException("Not logged in");
        }
        return (User) user;
    }

    public static String token(HttpServletRequest request) {
        return (String) request.getAttribute(AuthInterceptor.TOKEN_ATTRIBUTE);
    }

    public static User requireRole(HttpServletRequest request, Role role) {
        User user = get(request);
        if (user.role() != role) {
            throw new ForbiddenException("Requires role " + role);
        }
        return user;
    }
}

