package com.tech.techkuppiapp.service;

import com.tech.techkuppiapp.entity.QuestionBank;
import com.tech.techkuppiapp.entity.QuizUser;
import com.tech.techkuppiapp.entity.UserAnswer;
import com.tech.techkuppiapp.repository.QuestionBankRepository;
import com.tech.techkuppiapp.repository.QuizUserRepository;
import com.tech.techkuppiapp.repository.UserAnswerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Finds or creates quiz users and persists their answers for the leaderboard.
 */
@Service
public class QuizUserService {

    private static final Logger log = LoggerFactory.getLogger(QuizUserService.class);

    private final QuizUserRepository quizUserRepository;
    private final UserAnswerRepository userAnswerRepository;
    private final QuestionBankRepository questionBankRepository;

    public QuizUserService(QuizUserRepository quizUserRepository,
                          UserAnswerRepository userAnswerRepository,
                          QuestionBankRepository questionBankRepository) {
        this.quizUserRepository = quizUserRepository;
        this.userAnswerRepository = userAnswerRepository;
        this.questionBankRepository = questionBankRepository;
    }

    @Transactional
    public QuizUser findOrCreateByTelegramUser(long telegramUserId, String displayName, String username) {
        Optional<QuizUser> existing = quizUserRepository.findByTelegramUserId(telegramUserId);
        if (existing.isPresent()) {
            QuizUser u = existing.get();
            if ((displayName != null && !displayName.equals(u.getDisplayName())) || (username != null && !username.equals(u.getUsername()))) {
                u.setDisplayName(displayName != null && !displayName.isBlank() ? displayName : u.getDisplayName());
                u.setUsername(username);
                quizUserRepository.save(u);
            }
            return u;
        }
        QuizUser user = new QuizUser(telegramUserId, displayName, username);
        user = quizUserRepository.save(user);
        log.debug("Created quiz user: telegramUserId={} displayName={}", telegramUserId, user.getDisplayName());
        return user;
    }

    /**
     * Persist all answers from an active question (e.g. when results are shown or question expires).
     *
     * @param userIdToAnswer   map of Telegram user id -> chosen option (A-D)
     * @param userIdToDisplay  map of Telegram user id -> display name
     * @param userIdToUsername optional map of Telegram user id -> username
     * @param correctAnswer    correct option for this question
     * @param questionBankId   optional question bank id if question came from bank
     * @param answeredAt       time to record for all answers (e.g. close time)
     */
    @Transactional
    public void persistAnswers(Map<Long, String> userIdToAnswer,
                               Map<Long, String> userIdToDisplay,
                               Map<Long, String> userIdToUsername,
                               String correctAnswer,
                               Long questionBankId,
                               Instant answeredAt) {
        if (userIdToAnswer == null || userIdToAnswer.isEmpty()) return;

        QuestionBank questionBank = null;
        if (questionBankId != null) {
            questionBank = questionBankRepository.findById(questionBankId).orElse(null);
        }

        String correct = correctAnswer != null ? correctAnswer.trim().toUpperCase() : "A";
        Instant at = answeredAt != null ? answeredAt : Instant.now();

        for (Map.Entry<Long, String> e : userIdToAnswer.entrySet()) {
            long telegramUserId = e.getKey();
            String chosen = e.getValue() != null ? e.getValue().trim().toUpperCase() : "?";
            String displayName = userIdToDisplay != null ? userIdToDisplay.get(telegramUserId) : null;
            if (displayName == null || displayName.isBlank()) displayName = "User" + telegramUserId;
            String username = userIdToUsername != null ? userIdToUsername.get(telegramUserId) : null;

            QuizUser user = findOrCreateByTelegramUser(telegramUserId, displayName, username);
            boolean isCorrect = correct.equals(chosen);
            UserAnswer answer = new UserAnswer(user, questionBank, chosen, isCorrect, at);
            userAnswerRepository.save(answer);
        }
        log.info("Persisted {} answers for question (correct={})", userIdToAnswer.size(), correct);
    }
}
