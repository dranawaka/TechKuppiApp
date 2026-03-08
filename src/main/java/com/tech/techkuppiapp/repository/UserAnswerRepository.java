package com.tech.techkuppiapp.repository;

import com.tech.techkuppiapp.entity.QuizUser;
import com.tech.techkuppiapp.entity.UserAnswer;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserAnswerRepository extends JpaRepository<UserAnswer, Long> {

    @Query(value = """
        SELECT user_id, COUNT(*) AS score
        FROM user_answer
        WHERE correct = true
        GROUP BY user_id
        ORDER BY score DESC
        """, nativeQuery = true)
    List<Object[]> findLeaderboardScores(Pageable pageable);

    long countByUserAndCorrectTrue(QuizUser user);
}
