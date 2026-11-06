package com.tradesim.tradesim.controller;

import com.tradesim.tradesim.domain.Role;
import com.tradesim.tradesim.dto.AddStockRequest;
import com.tradesim.tradesim.dto.AdminUserSummary;
import com.tradesim.tradesim.dto.DepositRequest;
import com.tradesim.tradesim.dto.StockResponse;
import com.tradesim.tradesim.dto.WalletResponse;
import com.tradesim.tradesim.security.CurrentUser;
import com.tradesim.tradesim.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public List<AdminUserSummary> listUsers(HttpServletRequest request) {
        CurrentUser.requireRole(request, Role.ADMIN);
        return adminService.listUsers();
    }

    @PostMapping("/users/{id}/deposit")
    public WalletResponse deposit(HttpServletRequest request,
                                  @PathVariable Long id,
                                  @Valid @RequestBody DepositRequest body) {
        CurrentUser.requireRole(request, Role.ADMIN);
        return adminService.depositToTrader(id, body.amount());
    }

    @PostMapping("/users/{id}/reset")
    public WalletResponse reset(HttpServletRequest request, @PathVariable Long id) {
        CurrentUser.requireRole(request, Role.ADMIN);
        return adminService.resetTraderAccount(id);
    }

    @PostMapping("/stocks")
    @ResponseStatus(HttpStatus.CREATED)
    public StockResponse addStock(HttpServletRequest request,
                                  @Valid @RequestBody AddStockRequest body) {
        CurrentUser.requireRole(request, Role.ADMIN);
        return StockResponse.from(adminService.addStock(body));
    }

    @DeleteMapping("/stocks/{symbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delistStock(HttpServletRequest request, @PathVariable String symbol) {
        CurrentUser.requireRole(request, Role.ADMIN);
        adminService.delistStock(symbol);
    }
}

