package com.tradesim.tradesim.controller;

import com.tradesim.tradesim.domain.Session;
import com.tradesim.tradesim.dto.LoginRequest;
import com.tradesim.tradesim.dto.LoginResponse;
import com.tradesim.tradesim.dto.RegisterRequest;
import com.tradesim.tradesim.dto.UserResponse;
import com.tradesim.tradesim.security.CurrentUser;
import com.tradesim.tradesim.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = UserResponse.from(authService.register(request));
        Session session = authService.login(new LoginRequest(request.username(), request.password()));
        return new LoginResponse(session.token(), session.expiresAt(), user);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Session session = authService.login(request);
        UserResponse user = UserResponse.from(authService.getUser(session.userId()));
        return new LoginResponse(session.token(), session.expiresAt(), user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        authService.logout(CurrentUser.token(request));
    }

    @GetMapping("/me")
    public UserResponse me(HttpServletRequest request) {
        return UserResponse.from(CurrentUser.get(request));
    }
}

