package com.tech.techkuppiapp.controller;

import com.tech.techkuppiapp.dto.BatchQuestionsRequest;
import com.tech.techkuppiapp.dto.BatchQuestionsResponse;
import com.tech.techkuppiapp.entity.QuestionBank;
import com.tech.techkuppiapp.service.BatchQuestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API for the question bank, including batch generation from the AI model.
 */
@RestController
@RequestMapping("/api/questions")
public class QuestionBankController {

    private final BatchQuestionService batchQuestionService;

    public QuestionBankController(BatchQuestionService batchQuestionService) {
        this.batchQuestionService = batchQuestionService;
    }

    /**
     * Pull a list of questions from the AI model and save them to the database in a batch.
     * Request body is optional; defaults: count=5, no topic (generic questions).
     *
     * Example: POST /api/questions/batch
     * Body: { "count": 5, "topic": "aws" }
     */
    @PostMapping("/batch")
    public ResponseEntity<BatchQuestionsResponse> generateAndSaveBatch(
            @RequestBody(required = false) BatchQuestionsRequest request) {

        int count = request != null && request.getCount() != null ? request.getCount() : 5;
        String topic = request != null ? request.getTopic() : null;

        List<QuestionBank> saved = batchQuestionService.generateAndSaveBatch(count, topic);

        if (saved.isEmpty()) {
            return ResponseEntity.ok(
                    BatchQuestionsResponse.error("No questions could be generated or parsed. Check AI provider (app.ai.provider) and prompt."));
        }

        List<Long> ids = saved.stream().map(QuestionBank::getId).collect(Collectors.toList());
        return ResponseEntity.ok(BatchQuestionsResponse.ok(count, saved.size(), ids));
    }
}
