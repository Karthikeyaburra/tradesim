package com.tradesim.tradesim.controller;

import com.tradesim.tradesim.domain.Role;
import com.tradesim.tradesim.domain.User;
import com.tradesim.tradesim.dto.PortfolioResponse;
import com.tradesim.tradesim.security.CurrentUser;
import com.tradesim.tradesim.service.PortfolioService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    public PortfolioResponse getMyPortfolio(HttpServletRequest request) {
        User trader = CurrentUser.requireRole(request, Role.TRADER);
        return portfolioService.getPortfolio(trader.id());
    }
}

