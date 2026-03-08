package com.tech.techkuppiapp.controller;

import com.tech.techkuppiapp.service.LeaderboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for the quiz leaderboard.
 */
@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /**
     * Get leaderboard: users ranked by number of correct answers.
     * Optional query param: limit (default 10, max 100).
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getLeaderboard(
            @RequestParam(defaultValue = "10") int limit) {
        List<LeaderboardService.LeaderboardEntry> entries = leaderboardService.getLeaderboard(limit);
        List<Map<String, Object>> body = entries.stream()
                .map(e -> Map.<String, Object>of(
                        "rank", e.getRank(),
                        "displayName", e.getDisplayName(),
                        "username", e.getUsername() != null ? e.getUsername() : "",
                        "score", e.getScore()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(body);
    }
}
