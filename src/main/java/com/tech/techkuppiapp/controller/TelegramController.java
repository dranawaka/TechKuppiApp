package com.tech.techkuppiapp.controller;


import com.tech.techkuppiapp.dto.PollRequest;
import com.tech.techkuppiapp.service.TelegramBotService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/telegram")
public class TelegramController {

    private final TelegramBotService botService;

    @Value("${telegram.bot.chatid}")
    private String chatId;

    public TelegramController(TelegramBotService botService) {
        this.botService = botService;
    }

    @PostMapping("/sendPoll")
    public String sendPoll(@RequestBody PollRequest pollRequest) {
        botService.sendPoll(pollRequest.getQuestion(), pollRequest.getOptions());
        return "Poll sent successfully!";
    }

    @GetMapping("/send")
    public String send() {
        botService.pushQuestion(chatId, "Hello World!");
        return "Message sent to Telegram group!";
    }

    // API to generate and send a question
    @PostMapping("/generateQuestion")
    public String generateAndSendQuestion() {
        botService.sendGeneratedQuestion();
        return "Question generated and sent!";
    }

}