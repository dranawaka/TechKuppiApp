package com.tech.techkuppiapp.service;

import com.tech.techkuppiapp.entity.QuizUser;
import com.tech.techkuppiapp.repository.QuizUserRepository;
import com.tech.techkuppiapp.repository.UserAnswerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Returns leaderboard: users ranked by number of correct answers.
 */
@Service
public class LeaderboardService {

    private final UserAnswerRepository userAnswerRepository;
    private final QuizUserRepository quizUserRepository;

    public LeaderboardService(UserAnswerRepository userAnswerRepository,
                              QuizUserRepository quizUserRepository) {
        this.userAnswerRepository = userAnswerRepository;
        this.quizUserRepository = quizUserRepository;
    }

    /**
     * Leaderboard entry: rank, display name, score (correct count).
     */
    public static class LeaderboardEntry {
        private final int rank;
        private final String displayName;
        private final String username;
        private final long score;

        public LeaderboardEntry(int rank, String displayName, String username, long score) {
            this.rank = rank;
            this.displayName = displayName;
            this.username = username;
            this.score = score;
        }

        public int getRank() { return rank; }
        public String getDisplayName() { return displayName; }
        public String getUsername() { return username; }
        public long getScore() { return score; }
    }

    /**
     * Get top users by correct answers.
     *
     * @param limit max number of entries (default 10)
     * @return list of entries ordered by score descending
     */
    public List<LeaderboardEntry> getLeaderboard(int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        List<Object[]> rows = userAnswerRepository.findLeaderboardScores(PageRequest.of(0, safeLimit));

        List<LeaderboardEntry> result = new ArrayList<>();
        int rank = 1;
        for (Object[] row : rows) {
            Long userId = ((Number) row[0]).longValue();
            long score = ((Number) row[1]).longValue();
            Optional<QuizUser> userOpt = quizUserRepository.findById(userId);
            String displayName = userOpt.map(QuizUser::getDisplayName).orElse("User#" + userId);
            String username = userOpt.map(QuizUser::getUsername).orElse(null);
            result.add(new LeaderboardEntry(rank++, displayName, username, score));
        }
        return result;
    }
}
