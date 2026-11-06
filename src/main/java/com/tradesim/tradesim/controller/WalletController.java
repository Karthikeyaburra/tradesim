package com.tradesim.tradesim.controller;

import com.tradesim.tradesim.domain.Role;
import com.tradesim.tradesim.domain.User;
import com.tradesim.tradesim.dto.WalletResponse;
import com.tradesim.tradesim.security.CurrentUser;
import com.tradesim.tradesim.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public WalletResponse getMyWallet(HttpServletRequest request) {
        // Admins have no wallet and don't trade -> returns 403 if an admin calls this.
        User trader = CurrentUser.requireRole(request, Role.TRADER);
        return walletService.getWalletView(trader.id());
    }
}

