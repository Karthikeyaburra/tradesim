package com.tradesim.tradesim.service;

import com.tradesim.tradesim.domain.Money;
import com.tradesim.tradesim.domain.Position;
import com.tradesim.tradesim.domain.Wallet;
import com.tradesim.tradesim.dto.HoldingResponse;
import com.tradesim.tradesim.dto.PortfolioResponse;
import com.tradesim.tradesim.repository.HoldingRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PortfolioService {

    private final WalletService walletService;
    private final HoldingRepository holdingRepository;

    public PortfolioService(WalletService walletService, HoldingRepository holdingRepository) {
        this.walletService = walletService;
        this.holdingRepository = holdingRepository;
    }

    public PortfolioResponse getPortfolio(Long userId) {
        Wallet wallet = walletService.getWallet(userId);
        List<Position> positions = holdingRepository.findPositionsByUserId(userId);

        BigDecimal holdingsValue = Money.ZERO;
        for (Position p : positions) {
            holdingsValue = holdingsValue.add(p.marketValue());
        }

        BigDecimal totalValue = wallet.totalCash().add(holdingsValue);
        BigDecimal totalPnl = totalValue.subtract(wallet.netDeposits());
        BigDecimal totalPnlPercent = Money.percent(totalPnl, wallet.netDeposits());

        List<HoldingResponse> holdingDtos = positions.stream()
                .map(HoldingResponse::from)
                .toList();

        return new PortfolioResponse(
                wallet.balance(),
                wallet.reservedCash(),
                holdingsValue,
                totalValue,
                wallet.netDeposits(),
                totalPnl,
                totalPnlPercent,
                holdingDtos
        );
    }
}

