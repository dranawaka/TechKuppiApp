package com.tech.techkuppiapp.scheduler;

import com.tech.techkuppiapp.service.TelegramBotService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QuestionScheduler {

    private final TelegramBotService botService;

    @Value("${telegram.bot.chatid}")
    private String chatId;

    public QuestionScheduler(TelegramBotService botService) {
        this.botService = botService;
    }

    @Scheduled(fixedRate = 600000) // Sends message every 60 seconds
    public void sendQuestion() {
        String question = "What is the most exciting thing you've learned today?";
        botService.pushQuestion(chatId, question);
    }
}
