package com.tradesim.tradesim.controller;

import com.tradesim.tradesim.dto.LeaderboardEntry;
import com.tradesim.tradesim.security.CurrentUser;
import com.tradesim.tradesim.service.LeaderboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping
    public List<LeaderboardEntry> getLeaderboard(HttpServletRequest request) {
        CurrentUser.get(request);
        return leaderboardService.getLeaderboard();
    }
}

