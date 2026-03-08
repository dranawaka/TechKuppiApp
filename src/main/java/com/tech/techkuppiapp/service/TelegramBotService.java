package com.tech.techkuppiapp.service;

import com.tech.techkuppiapp.dto.GeneratedQuestion;
import com.tech.techkuppiapp.entity.QuestionBank;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnExpression("!'${telegram.bot.token:}'.isBlank()")
public class TelegramBotService extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);
    private static final String ADVANCED_LEVEL = " The question must be advanced, architect-level, or tech lead-level—suitable for senior engineers and system designers; avoid beginner or intermediate content.";
    private static final String QUESTION_PROMPT = "Generate one exam-style multiple-choice question for AWS DVA-C02 with exactly 4 possible answers." + ADVANCED_LEVEL
            + " Return only a valid JSON object with keys: \"question\" (string), \"options\" (array of exactly 4 strings, in order A to D), and \"correctAnswer\" (string: \"A\", \"B\", \"C\", or \"D\"). No markdown, no extra text.";
    private static final String QUESTION_PROMPT_GENERIC = "Generate one multiple-choice question with exactly 4 possible answers." + ADVANCED_LEVEL
            + " Return only a valid JSON object with keys: \"question\" (string), \"options\" (array of exactly 4 strings, in order A to D), and \"correctAnswer\" (string: \"A\", \"B\", \"C\", or \"D\"). No markdown, no extra text.";

    private static final String[] SUPPORTED_TOPICS = {"java", "aws", "kafka", "database", "spring", "springboot", "genai"};

    /** Per-chat active question: question text, options, correct answer, user answers, and when it was sent. */
    private static final class ActiveQuestion {
        final String questionText;
        final List<String> options;
        final String correctAnswer;
        final Map<Long, String> userIdToAnswer = new ConcurrentHashMap<>();
        final Map<Long, String> userIdToDisplayName = new ConcurrentHashMap<>();
        final Map<Long, String> userIdToUsername = new ConcurrentHashMap<>();
        final Long questionBankId;
        final long sentAtMs;
        final long resultsDelayMs;

        ActiveQuestion(GeneratedQuestion q, long resultsDelayMs) {
            this(q, resultsDelayMs, null);
        }

        ActiveQuestion(GeneratedQuestion q, long resultsDelayMs, Long questionBankId) {
            this.questionText = q.getQuestion();
            this.options = q.getOptions() != null ? List.copyOf(q.getOptions()) : List.of();
            this.correctAnswer = normalizeOption(q.getCorrectAnswer());
            this.questionBankId = questionBankId;
            this.sentAtMs = System.currentTimeMillis();
            this.resultsDelayMs = resultsDelayMs > 0 ? resultsDelayMs : 2 * 60 * 1000;
        }

        boolean recordAnswer(long userId, String displayName, String username, String answer) {
            String letter = normalizeOption(answer);
            if (letter != null && !userIdToAnswer.containsKey(userId)) {
                userIdToAnswer.put(userId, letter);
                userIdToDisplayName.put(userId, displayName != null && !displayName.isBlank() ? displayName : "User" + userId);
                if (username != null && !username.isBlank()) userIdToUsername.put(userId, username);
                return true;
            }
            return false;
        }

        boolean isExpired(long nowMs) {
            return (nowMs - sentAtMs) >= resultsDelayMs;
        }

        String buildResultsMessage() {
            String correct = correctAnswer != null ? correctAnswer : "?";
            List<String> correctUsers = new ArrayList<>();
            List<String[]> wrongUsers = new ArrayList<>(); // [displayName, chosenLetter]
            for (Map.Entry<Long, String> e : userIdToAnswer.entrySet()) {
                String name = userIdToDisplayName.getOrDefault(e.getKey(), "User" + e.getKey());
                String chosen = e.getValue();
                if (correct.equals(chosen)) {
                    correctUsers.add(name);
                } else {
                    wrongUsers.add(new String[]{name, chosen != null ? chosen : "?"});
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Correct answer: ").append(correct).append("\n\n");
            sb.append("Correct users:\n");
            if (correctUsers.isEmpty()) {
                sb.append("(none)\n");
            } else {
                for (String u : correctUsers) sb.append("✅ ").append(u).append("\n");
            }
            sb.append("\nWrong answers:\n");
            if (wrongUsers.isEmpty()) {
                sb.append("(none)\n");
            } else {
                for (String[] w : wrongUsers) {
                    sb.append("❌ ").append(w[0]).append(" (chose ").append(w[1]).append(")\n");
                }
            }
            return sb.toString();
        }
    }

    /** Accepts A-D, a-d, 1-4, "A.", "A)", "option A", etc. */
    private static String normalizeOption(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toUpperCase();
        if (s.isEmpty()) return null;
        char first = s.charAt(0);
        if (first >= 'A' && first <= 'D') return String.valueOf(first);
        if (first >= '1' && first <= '4') return String.valueOf((char) ('A' + (first - '1')));
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'D') return String.valueOf(c);
            if (c >= '1' && c <= '4') return String.valueOf((char) ('A' + (c - '1')));
        }
        return null;
    }

    private final Map<String, ActiveQuestion> activeQuestionsByChat = new ConcurrentHashMap<>();

    private final String botUsername;
    private final String defaultChatId;
    private final OpenAIService openAIService;
    private final QuestionBankService questionBankService;
    private final QuizUserService quizUserService;
    private final LeaderboardService leaderboardService;
    /** Answer time in seconds before results are shown (e.g. 30). */
    private final int answerSeconds;
    /** Question source: "openai" or "question-bank". When question-bank, topic is ignored. */
    private final String questionSource;

    public TelegramBotService(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            @Value("${telegram.bot.chatid}") String chatId,
            @Value("${telegram.quiz.answer-seconds:30}") int answerSeconds,
            @Value("${telegram.quiz.question-source:openai}") String questionSource,
            OpenAIService openAIService,
            QuestionBankService questionBankService,
            QuizUserService quizUserService,
            LeaderboardService leaderboardService) {
        super(requireNonBlank(botToken, "telegram.bot.token must be set (e.g. TELEGRAM_BOT_TOKEN)"));
        this.botUsername = botUsername != null ? botUsername : "";
        this.defaultChatId = chatId != null ? chatId : "";
        this.answerSeconds = answerSeconds > 0 ? answerSeconds : 30;
        this.questionSource = questionSource != null && !questionSource.isBlank() ? questionSource.trim().toLowerCase() : "openai";
        this.openAIService = openAIService;
        this.questionBankService = questionBankService;
        this.quizUserService = quizUserService;
        this.leaderboardService = leaderboardService;
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    /** Generate and send a question to the default chat. */
    public void sendGeneratedQuestion() {
        sendGeneratedQuestion(defaultChatId, QUESTION_PROMPT);
    }

    /** Generate and send a question to the given chat. */
    public void sendGeneratedQuestion(String targetChatId) {
        sendGeneratedQuestion(targetChatId, QUESTION_PROMPT_GENERIC);
    }

    /** Sends a random question from the question bank to the given chat. Sends a fallback message if the bank is empty. */
    public void sendRandomQuestionFromBank(String targetChatId) {
        questionBankService.getRandomQuestionEntity()
                .map(q -> new ResolvedQuestion(
                        new GeneratedQuestion(q.getQuestion(), q.getOptions(), q.getCorrectAnswer()),
                        q.getId()))
                .ifPresentOrElse(
                        r -> sendResolvedQuestion(targetChatId, r),
                        () -> sendMessage(targetChatId, "No questions in the bank yet. Add questions via POST /api/questions/batch.")
                );
    }

    /** Generate and send a topic-based question to the given chat. Returns true if topic was valid and question sent. When source is question-bank, topic is ignored. */
    public boolean sendGeneratedQuestionForTopic(String targetChatId, String topic) {
        String prompt = "question-bank".equals(questionSource) ? QUESTION_PROMPT_GENERIC : buildTopicPrompt(topic);
        if (prompt == null) return false;
        resolveQuestion(prompt).ifPresentOrElse(
                r -> sendResolvedQuestion(targetChatId, r),
                () -> sendMessage(targetChatId, "Sorry, I couldn't get a question right now. Try again later or add questions via POST /api/questions/batch.")
        );
        return true;
    }

    /** Build OpenAI prompt for a topic. Returns null if topic is not supported. */
    public static String buildTopicPrompt(String topic) {
        if (topic == null || topic.isBlank()) return null;
        String t = topic.trim().toLowerCase();
        String subject;
        switch (t) {
            case "java":
                subject = "Java programming and interviews (core Java, collections, concurrency, JVM)";
                break;
            case "aws":
                subject = "AWS (DVA-C02 style: services, serverless, DynamoDB, S3, Lambda, etc.)";
                break;
            case "kafka":
                subject = "Apache Kafka (concepts, brokers, topics, consumers, producers, partitioning)";
                break;
            case "database":
                subject = "Databases and SQL (relational DBs, queries, indexing, transactions, NoSQL basics)";
                break;
            case "spring":
                subject = "Spring Framework (core, IoC, AOP, MVC, security, transactions, testing)";
                break;
            case "springboot":
                subject = "Spring Boot (auto-configuration, starters, Actuator, embedded servers, profiles, production readiness)";
                break;
            case "genai":
                subject = "Generative AI (LLMs, RAG, prompt engineering, agents, embeddings, model evaluation, production GenAI systems)";
                break;
            default:
                return null;
        }
        return "Generate one multiple-choice interview-style question about " + subject + " with exactly 4 possible answers. The question must be advanced, architect-level, or tech lead-level—suitable for senior engineers and system designers; avoid beginner or intermediate content. "
                + "Return only a valid JSON object with keys: \"question\" (string), \"options\" (array of exactly 4 strings, in order A to D), and \"correctAnswer\" (string: \"A\", \"B\", \"C\", or \"D\"). No markdown, no extra text.";
    }

    public static boolean isSupportedTopic(String topic) {
        if (topic == null || topic.isBlank()) return false;
        String t = topic.trim().toLowerCase();
        for (String s : SUPPORTED_TOPICS) if (s.equals(t)) return true;
        return false;
    }

    private void sendGeneratedQuestion(String targetChatId, String prompt) {
        resolveQuestion(prompt).ifPresentOrElse(
                r -> sendResolvedQuestion(targetChatId, r),
                () -> sendMessage(targetChatId, "Sorry, I couldn't get a question right now. Try again later or add questions via POST /api/questions/batch.")
        );
    }

    /** Send a resolved question (save to bank if from OpenAI and get id, then send). */
    private void sendResolvedQuestion(String targetChatId, ResolvedQuestion r) {
        Long id = r.questionBankId();
        if (id == null) {
            QuestionBank saved = questionBankService.saveFromGenerated(r.question());
            id = saved != null ? saved.getId() : null;
        }
        sendQuestionAsText(targetChatId, r.question(), id);
    }

    /**
     * Resolves the next question from the configured source (openai or question-bank).
     * When question-bank, openAiPrompt is ignored and a random question is returned with its id.
     * When openai, generates using the prompt and returns question with id=null (caller should save to get id).
     */
    public Optional<ResolvedQuestion> resolveQuestion(String openAiPrompt) {
        if ("question-bank".equals(questionSource)) {
            return questionBankService.getRandomQuestionEntity()
                    .map(q -> new ResolvedQuestion(
                            new GeneratedQuestion(q.getQuestion(), q.getOptions(), q.getCorrectAnswer()),
                            q.getId()));
        }
        GeneratedQuestion g = generateQuestion(openAiPrompt);
        return g != null ? Optional.of(new ResolvedQuestion(g, null)) : Optional.empty();
    }

    private record ResolvedQuestion(GeneratedQuestion question, Long questionBankId) {}

    /**
     * Resolves the next question (from configured source) and sends it to the chat, or sends a fallback message if none available.
     * Used by the scheduler and any caller that wants one question sent.
     */
    public void sendNextQuestion(String chatId, String openAiPrompt) {
        resolveQuestion(openAiPrompt).ifPresentOrElse(
                r -> sendResolvedQuestion(chatId, r),
                () -> {
                    pushQuestion(chatId, "What is the most exciting thing you've learned today?");
                    log.debug("Sent fallback text question (no question available from configured source)");
                }
        );
    }

    /**
     * Calls OpenAI and parses response into a question and options. Returns null on failure or invalid response.
     */
    public GeneratedQuestion generateQuestion(String prompt) {
        String response = openAIService.getChatGPTResponse(prompt);
        return parseGeneratedQuestion(response);
    }

    /** Parse OpenAI response (expects JSON with "question", "options", and "correctAnswer"). */
    static GeneratedQuestion parseGeneratedQuestion(String response) {
        if (response == null || response.isBlank()) return null;
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) trimmed = trimmed.substring(start + 1, end).trim();
            else if (start >= 0) trimmed = trimmed.substring(start + 1).trim();
        }
        try {
            return parseGeneratedQuestion(new JSONObject(trimmed));
        } catch (Exception e) {
            log.warn("Failed to parse generated question JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse OpenAI response that is a JSON array of question objects. Each object has "question", "options", "correctAnswer".
     * Returns only successfully parsed questions; invalid entries are skipped.
     */
    public static List<GeneratedQuestion> parseGeneratedQuestionList(String response) {
        List<GeneratedQuestion> result = new ArrayList<>();
        if (response == null || response.isBlank()) return result;
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) trimmed = trimmed.substring(start + 1, end).trim();
            else if (start >= 0) trimmed = trimmed.substring(start + 1).trim();
        }
        try {
            JSONArray arr = new JSONArray(trimmed);
            for (int i = 0; i < arr.length(); i++) {
                GeneratedQuestion q = parseGeneratedQuestion(arr.optJSONObject(i));
                if (q != null) result.add(q);
            }
        } catch (Exception e) {
            log.warn("Failed to parse generated question list JSON: {}", e.getMessage());
        }
        return result;
    }

    private static GeneratedQuestion parseGeneratedQuestion(JSONObject json) {
        if (json == null) return null;
        String question = json.optString("question", null);
        if (question == null || question.isBlank()) return null;
        JSONArray arr = json.optJSONArray("options");
        if (arr == null || arr.length() < 2) return null;
        List<String> options = new ArrayList<>();
        for (int j = 0; j < Math.min(4, arr.length()); j++) {
            String opt = arr.optString(j, "").trim();
            if (!opt.isEmpty()) options.add(opt);
        }
        if (options.size() < 2) return null;
        String correctAnswer = json.optString("correctAnswer", "A").trim().toUpperCase();
        if (correctAnswer.isEmpty() || correctAnswer.length() > 1) correctAnswer = "A";
        char c = correctAnswer.charAt(0);
        if (c < 'A' || c > 'D') correctAnswer = "A";
        return new GeneratedQuestion(question, options, correctAnswer);
    }

    public void pushQuestion(String chatId, String question) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(question);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logSendError(chatId, "message", e);
        }
    }

    /** Sends the question as formatted text (Question: ... A) ... B) ... etc.) and stores it for answer collection. */
    public void sendQuestionAsText(String chatId, GeneratedQuestion generated) {
        sendQuestionAsText(chatId, generated, (Long) null);
    }

    /** Sends the question with optional question bank id (for persisting answers to DB). */
    public void sendQuestionAsText(String chatId, GeneratedQuestion generated, Long questionBankId) {
        long delayMs = answerSeconds * 1000L;
        sendQuestionAsText(chatId, generated, delayMs, questionBankId);
    }

    /** Sends the question with a custom results delay. */
    public void sendQuestionAsText(String chatId, GeneratedQuestion generated, long resultsDelayMs) {
        sendQuestionAsText(chatId, generated, resultsDelayMs, null);
    }

    /** Sends the question with a custom results delay and optional question bank id. */
    public void sendQuestionAsText(String chatId, GeneratedQuestion generated, long resultsDelayMs, Long questionBankId) {
        List<String> options = generated.getOptions();
        if (options == null || options.size() < 2) {
            log.warn("Question requires at least 2 options");
            return;
        }
        String chatIdKey = chatId != null ? chatId.trim() : "";
        String[] letters = {"A", "B", "C", "D"};
        StringBuilder text = new StringBuilder("Question: ").append(generated.getQuestion()).append("\n\n");
        for (int i = 0; i < Math.min(4, options.size()); i++) {
            text.append(letters[i]).append(") ").append(options.get(i).trim()).append("\n");
        }
        int seconds = (int) (resultsDelayMs / 1000);
        text.append("\nReply with A, B, C, or D to answer.");
        text.append("\n\n⏱ You have ").append(seconds).append(" seconds to answer.");
        SendMessage message = new SendMessage();
        message.setChatId(chatIdKey);
        message.setText(text.toString());
        ActiveQuestion activeQuestion = new ActiveQuestion(generated, resultsDelayMs, questionBankId);
        try {
            Message sent = execute(message);
            if (sent != null && sent.getChat() != null) {
                String actualChatId = sent.getChat().getId().toString();
                activeQuestionsByChat.put(actualChatId, activeQuestion);
                if (!actualChatId.equals(chatIdKey)) {
                    log.info("Question sent to chat {} (Telegram chat id {}); storing under {} for answer matching", chatIdKey, actualChatId, actualChatId);
                }
            } else {
                activeQuestionsByChat.put(chatIdKey, activeQuestion);
            }
        } catch (TelegramApiException e) {
            logSendError(chatIdKey, "question", e);
            activeQuestionsByChat.put(chatIdKey, activeQuestion);
        }
        log.info("Question sent as text to chat {} ({}s to answer)", chatIdKey, seconds);
    }

    private void sendLeaderboard(String chatId) {
        List<LeaderboardService.LeaderboardEntry> entries = leaderboardService.getLeaderboard(10);
        if (entries.isEmpty()) {
            sendMessage(chatId, "🏆 Leaderboard\n\nNo scores yet. Answer quiz questions to appear here!");
            return;
        }
        StringBuilder sb = new StringBuilder("🏆 Leaderboard\n\n");
        for (LeaderboardService.LeaderboardEntry e : entries) {
            String name = e.getUsername() != null && !e.getUsername().isBlank()
                    ? e.getDisplayName() + " (@" + e.getUsername() + ")" : e.getDisplayName();
            sb.append(e.getRank()).append(". ").append(name).append(" — ").append(e.getScore()).append(" correct\n");
        }
        sendMessage(chatId, sb.toString());
    }

    /** Closes the active question in this chat and sends the results. No-op if there is no active question. */
    public void showResults(String chatId) {
        if (chatId == null || chatId.isBlank()) return;
        String key = chatId.trim();
        ActiveQuestion active = activeQuestionsByChat.remove(key);
        if (active == null) return;
        persistAnswersToDb(active);
        String msg = active.buildResultsMessage();
        sendMessage(key, msg);
        int count = active.userIdToAnswer.size();
        log.info("Results sent to chat {} ({} answers)", key, count);
    }

    /** Called by scheduler: close any question that has passed the results delay and send results. */
    public void closeExpiredQuestions() {
        long now = System.currentTimeMillis();
        List<String> toClose = new ArrayList<>();
        for (Map.Entry<String, ActiveQuestion> e : activeQuestionsByChat.entrySet()) {
            if (e.getValue().isExpired(now)) toClose.add(e.getKey());
        }
        for (String chatId : toClose) {
            ActiveQuestion active = activeQuestionsByChat.remove(chatId);
            if (active != null) {
                persistAnswersToDb(active);
                sendMessage(chatId, active.buildResultsMessage());
                log.info("Results sent to chat {} (expired, answers: {})", chatId, active.userIdToAnswer.size());
            }
        }
    }

    private void persistAnswersToDb(ActiveQuestion active) {
        try {
            quizUserService.persistAnswers(
                    active.userIdToAnswer,
                    active.userIdToDisplayName,
                    active.userIdToUsername,
                    active.correctAnswer,
                    active.questionBankId,
                    Instant.now());
        } catch (Exception e) {
            log.warn("Failed to persist answers to database: {}", e.getMessage());
        }
    }

    private void logSendError(String chatId, String what, TelegramApiException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("chat not found") || msg.contains("400")) {
            log.error("Failed to send {} to chat {}: {}. " +
                    "Ensure the user has opened a chat with this bot and sent /start, and that the chat ID is correct.",
                    what, chatId, e.getMessage());
        } else {
            log.error("Failed to send {} to chat {}: {}", what, chatId, e.getMessage());
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String userMessage = update.getMessage().getText().trim();
            String chatId = update.getMessage().getChatId().toString().trim();

            ActiveQuestion active = activeQuestionsByChat.get(chatId);
            if (active == null && defaultChatId != null && !defaultChatId.isBlank()) {
                String configId = defaultChatId.trim();
                if (!configId.equals(chatId)) {
                    active = activeQuestionsByChat.get(configId);
                    if (active != null) {
                        activeQuestionsByChat.remove(configId);
                        activeQuestionsByChat.put(chatId, active);
                        log.debug("Mapped active question from config chat {} to update chat {}", configId, chatId);
                    }
                }
            }
            String option = normalizeOption(userMessage);
            if (active != null && option != null) {
                User from = update.getMessage().getFrom();
                long userId = from != null ? from.getId() : 0L;
                String displayName = from != null ? (from.getFirstName() != null ? from.getFirstName() : from.getUserName()) : "User";
                if (from != null && from.getLastName() != null && !from.getLastName().isBlank()) {
                    displayName = (from.getFirstName() != null ? from.getFirstName() + " " : "") + from.getLastName();
                }
                String username = from != null ? from.getUserName() : null;
                if (active.recordAnswer(userId, displayName, username, option)) {
                    log.info("Recorded answer chatId={} userId={} name={} option={}", chatId, userId, displayName, option);
                }
                return;
            }
            if (userMessage.equalsIgnoreCase("showResults") && active != null) {
                showResults(chatId);
                return;
            }
            if (userMessage.equalsIgnoreCase("/leaderboard") || userMessage.equalsIgnoreCase("leaderboard")) {
                sendLeaderboard(chatId);
                return;
            }
            if (userMessage.equalsIgnoreCase("generateQuestion")) {
                sendGeneratedQuestion(chatId);
            } else if (userMessage.toLowerCase().startsWith("/quiz")) {
                String topic = userMessage.length() > 5 ? userMessage.substring(5).trim() : "";
                if (!topic.isEmpty() && !isSupportedTopic(topic)) {
                    sendMessage(chatId, "Unknown topic. Use one of: java, aws, kafka, database, spring, springboot, genai");
                } else {
                    sendRandomQuestionFromBank(chatId);
                }
            } else if (userMessage.equalsIgnoreCase("/start") || userMessage.equalsIgnoreCase("help")) {
                sendMessage(chatId, "Quiz commands:\n• /quiz or /quiz <topic> – random question from the bank\n  Topics: java, aws, kafka, database, spring, springboot, genai\n• /leaderboard – See top scores\n\nYou have " + answerSeconds + " seconds to answer. Reply with A, B, C, or D. Send 'showResults' to see results early.");
            } else {
                sendMessage(chatId, "Send /quiz or /quiz <topic> for a random question from the bank, /leaderboard for scores, or 'help' for more.");
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
            logSendError(chatId, "message", e);
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }
}
