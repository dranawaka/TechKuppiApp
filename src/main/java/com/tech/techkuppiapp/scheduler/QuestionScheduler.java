package com.tech.techkuppiapp.scheduler;

import com.tech.techkuppiapp.service.TelegramBotService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(TelegramBotService.class)
public class QuestionScheduler {

    private final TelegramBotService botService;
    private final String chatId;
    private final boolean autoSendEnabled;

    public QuestionScheduler(TelegramBotService botService,
                              @Value("${telegram.bot.chatid}") String chatId,
                              @Value("${telegram.quiz.auto-send-enabled:false}") boolean autoSendEnabled) {
        this.botService = botService;
        this.chatId = chatId;
        this.autoSendEnabled = autoSendEnabled;
    }

    private static final String SCHEDULER_PROMPT =
            "Generate one exam-style multiple-choice question for AWS DVA-C02 with exactly 4 possible answers. The question must be advanced, architect-level, or tech lead-level—suitable for senior engineers and system designers; avoid beginner or intermediate content. "
                    + "Return only a valid JSON object with keys: \"question\" (string), \"options\" (array of exactly 4 strings, in order A to D), and \"correctAnswer\" (string: \"A\", \"B\", \"C\", or \"D\"). No markdown, no extra text.";

    /** Sends a question every 10 minutes when auto-send is enabled. Source is configurable (openai or question-bank). */
    @Scheduled(fixedRate = 600000) // 10 minutes
    public void sendQuestion() {
        if (!autoSendEnabled) {
            return;
        }
        botService.sendNextQuestion(chatId, SCHEDULER_PROMPT);
    }

    /** Periodically close any question that has passed the answer window (e.g. 30s) and send results. */
    @Scheduled(fixedRate = 15000) // every 15 seconds so 30s timer is enforced
    public void closeExpiredQuestions() {
        botService.closeExpiredQuestions();
    }
}
