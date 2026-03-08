package com.tech.techkuppiapp.scheduler;

import com.tech.techkuppiapp.dto.GeneratedQuestion;
import com.tech.techkuppiapp.entity.QuestionBank;
import com.tech.techkuppiapp.service.QuestionBankService;
import com.tech.techkuppiapp.service.TelegramBotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(TelegramBotService.class)
public class QuestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuestionScheduler.class);

    private final TelegramBotService botService;
    private final QuestionBankService questionBankService;
    private final String chatId;

    public QuestionScheduler(TelegramBotService botService,
                              QuestionBankService questionBankService,
                              @Value("${telegram.bot.chatid}") String chatId) {
        this.botService = botService;
        this.questionBankService = questionBankService;
        this.chatId = chatId;
    }

    /** Sends a question every 10 minutes as formatted text (Question + A/B/C/D). Users reply with A/B/C/D; results after 2 min. */
    @Scheduled(fixedRate = 600000) // 10 minutes
    public void sendQuestion() {
        GeneratedQuestion generated = botService.generateQuestion(
                "Generate one exam-style multiple-choice question for AWS DVA-C02 with exactly 4 possible answers. "
                        + "Return only a valid JSON object with keys: \"question\" (string), \"options\" (array of exactly 4 strings, in order A to D), and \"correctAnswer\" (string: \"A\", \"B\", \"C\", or \"D\"). No markdown, no extra text.");
        if (generated != null) {
            QuestionBank saved = questionBankService.saveFromGenerated(generated);
            botService.sendQuestionAsText(chatId, generated, saved != null ? saved.getId() : null);
        } else {
            botService.pushQuestion(chatId, "What is the most exciting thing you've learned today?");
            log.debug("Sent fallback text question (AI generation unavailable)");
        }
    }

    /** Periodically close any question that has passed the answer window (e.g. 30s) and send results. */
    @Scheduled(fixedRate = 15000) // every 15 seconds so 30s timer is enforced
    public void closeExpiredQuestions() {
        botService.closeExpiredQuestions();
    }
}
