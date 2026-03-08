package com.tech.techkuppiapp.controller;

import com.tech.techkuppiapp.dto.ApiResponse;
import com.tech.techkuppiapp.dto.GeneratedQuestion;
import com.tech.techkuppiapp.dto.PollRequest;
import com.tech.techkuppiapp.service.TelegramBotService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/telegram")
@ConditionalOnBean(TelegramBotService.class)
public class TelegramController {

    private final TelegramBotService botService;
    private final String chatId;

    public TelegramController(TelegramBotService botService,
                              @Value("${telegram.bot.chatid}") String chatId) {
        this.botService = botService;
        this.chatId = chatId;
    }

    @PostMapping("/sendPoll")
    public ResponseEntity<ApiResponse> sendPoll(@Valid @RequestBody PollRequest pollRequest) {
        botService.sendQuestionAsText(chatId, new GeneratedQuestion(
                pollRequest.getQuestion(), pollRequest.getOptions(), pollRequest.getCorrectAnswer()));
        return ResponseEntity.ok(ApiResponse.ok("Question sent successfully! Reply with A, B, C, or D. Results in 2 minutes or send 'showResults'."));
    }

    @GetMapping("/send")
    public ResponseEntity<ApiResponse> send() {
        botService.pushQuestion(chatId, "Hello World!");
        return ResponseEntity.ok(ApiResponse.ok("Message sent to Telegram group!"));
    }

    @PostMapping("/generateQuestion")
    public ResponseEntity<ApiResponse> generateAndSendQuestion() {
        botService.sendGeneratedQuestion();
        return ResponseEntity.ok(ApiResponse.ok("Question generated and sent!"));
    }
}
