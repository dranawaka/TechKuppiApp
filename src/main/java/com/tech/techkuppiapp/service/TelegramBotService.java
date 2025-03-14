package com.tech.techkuppiapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Arrays;
import java.util.List;

@Service
public class TelegramBotService extends TelegramLongPollingBot {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.chatid}")
    private String chatId;

    private final OpenAIService openAIService;

    public TelegramBotService(OpenAIService openAIService) {
        this.openAIService = openAIService;
    }


    public void sendGeneratedQuestion() {
        String prompt = "Generate a exam like multiple-choice question on aws-dva-c02 with 4 possible answers. Format: {question: '', options: ['', '', '', '']}";
        String response = openAIService.getChatGPTResponse(prompt);

        // Parse the response (assuming it's formatted correctly)
        String question = response.split("question: '")[1].split("'")[0];
        String[] optionsArray = response.split("options: \\[")[1].split("\\]")[0].replace("'", "").split(",");

        List<String> options = Arrays.asList(optionsArray);

        sendPoll(question, options);
    }

    public void sendGeneratedQuestion(String chatId) {
        String prompt = "Generate a multiple-choice question with 4 possible answers. Format: {question: '', options: ['', '', '', '']}";
        String response = openAIService.getChatGPTResponse(prompt);

        // Parse the response to extract question and options
        String question = response.split("question: '")[1].split("'")[0];
        String[] optionsArray = response.split("options: \\[")[1].split("\\]")[0].replace("'", "").split(",");

        List<String> options = Arrays.asList(optionsArray);

        sendPoll(chatId, question, options);
    }


    public void pushQuestion(String chatId, String question) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(question);
        try {
            execute(message); // Send the message
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendPoll(String question, List<String> options) {
        SendPoll poll = new SendPoll();
        poll.setChatId(chatId);
        poll.setQuestion(question);
        poll.setOptions(options);
        poll.setAllowMultipleAnswers(false);
        poll.setProtectContent(true);
        poll.setIsAnonymous(true); // Set to true if you want an anonymous poll

        try {
            execute(poll); // Send the poll
            System.out.println("✅ Poll sent successfully!");
        } catch (TelegramApiException e) {
            e.printStackTrace();
            System.err.println("❌ Failed to send poll: " + e.getMessage());
        }

    }

    public void sendPoll(String chatId, String question, List<String> options) {
        SendPoll poll = new SendPoll();
        poll.setChatId(chatId);
        poll.setQuestion(question);
        poll.setOptions(options);
        poll.setIsAnonymous(true);
        poll.setProtectContent(true);

        try {
            execute(poll);
            System.out.println("✅ Poll sent successfully!");
        } catch (TelegramApiException e) {
            e.printStackTrace();
            System.err.println("❌ Failed to send poll: " + e.getMessage());
        }
    }

        @Override
        public void onUpdateReceived(Update update) {
            if (update.hasMessage() && update.getMessage().hasText()) {
                String userMessage = update.getMessage().getText();
                Long chatId = update.getMessage().getChatId();

                if (userMessage.equalsIgnoreCase("generateQuestion")) {
                    sendGeneratedQuestion(chatId.toString()); // Generate and send a question
                } else {
                    sendMessage(chatId.toString(), "Send 'generateQuestion' to get a new quiz question!");
                }
            }
        }

    public void sendMessage(String chatId, String messageText) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }



}
