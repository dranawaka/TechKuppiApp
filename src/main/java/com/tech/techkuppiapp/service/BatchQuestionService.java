package com.tech.techkuppiapp.service;

import com.tech.techkuppiapp.dto.GeneratedQuestion;
import com.tech.techkuppiapp.entity.QuestionBank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Generates multiple questions from the AI model in one call and saves them to the question bank in a batch.
 */
@Service
public class BatchQuestionService {

    private static final Logger log = LoggerFactory.getLogger(BatchQuestionService.class);

    private static final int BATCH_MAX_TOKENS = 4000;

    private final OpenAIService openAIService;
    private final QuestionBankService questionBankService;

    public BatchQuestionService(OpenAIService openAIService, QuestionBankService questionBankService) {
        this.openAIService = openAIService;
        this.questionBankService = questionBankService;
    }

    /**
     * Request a batch of questions from the AI and save them to the database.
     *
     * @param count number of questions to generate (1–20)
     * @param topic optional topic (java, aws, kafka, database); null = generic
     * @return list of saved QuestionBank entities; may be smaller than count if parsing or API fails
     */
    public List<QuestionBank> generateAndSaveBatch(int count, String topic) {
        int safeCount = Math.max(1, Math.min(20, count));
        String prompt = buildBatchPrompt(safeCount, topic);
        log.info("Requesting batch of {} questions from AI (topic={})", safeCount, topic);

        String response = openAIService.getChatGPTResponse(prompt, BATCH_MAX_TOKENS);
        List<GeneratedQuestion> generated = TelegramBotService.parseGeneratedQuestionList(response);

        if (generated.isEmpty()) {
            log.warn("No valid questions parsed from AI batch response");
            return List.of();
        }

        List<QuestionBank> saved = questionBankService.saveAllFromGenerated(generated);
        log.info("Batch save complete: requested={}, parsed={}, saved={}", safeCount, generated.size(), saved.size());
        return saved;
    }

    private static String buildBatchPrompt(int count, String topic) {
        String subject = "multiple-choice exam-style questions with exactly 4 possible answers each";
        if (topic != null && !topic.isBlank()) {
            String t = topic.trim().toLowerCase();
            switch (t) {
                case "java":
                    subject = "multiple-choice Java interview questions (core Java, collections, concurrency, JVM) with exactly 4 possible answers each";
                    break;
                case "aws":
                    subject = "multiple-choice AWS DVA-C02 style questions (services, serverless, DynamoDB, S3, Lambda) with exactly 4 possible answers each";
                    break;
                case "kafka":
                    subject = "multiple-choice Apache Kafka questions (brokers, topics, consumers, producers, partitioning) with exactly 4 possible answers each";
                    break;
                case "database":
                    subject = "multiple-choice Database/SQL questions (relational DBs, queries, indexing, transactions) with exactly 4 possible answers each";
                    break;
                default:
                    break;
            }
        }

        return "Generate exactly " + count + " " + subject + ". "
                + "Return ONLY a valid JSON array. Each element must be an object with keys: \"question\" (string), \"options\" (array of exactly 4 strings, in order A to D), and \"correctAnswer\" (string: \"A\", \"B\", \"C\", or \"D\"). "
                + "No markdown, no code fences, no extra text—just the JSON array.";
    }
}
