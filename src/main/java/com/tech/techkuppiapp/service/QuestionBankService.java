package com.tech.techkuppiapp.service;

import com.tech.techkuppiapp.dto.GeneratedQuestion;
import com.tech.techkuppiapp.entity.QuestionBank;
import com.tech.techkuppiapp.repository.QuestionBankRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class QuestionBankService {

    private static final Logger log = LoggerFactory.getLogger(QuestionBankService.class);

    private final QuestionBankRepository questionBankRepository;

    public QuestionBankService(QuestionBankRepository questionBankRepository) {
        this.questionBankRepository = questionBankRepository;
    }

    /**
     * Returns a random question from the bank as a GeneratedQuestion, or empty if the bank is empty.
     */
    public Optional<GeneratedQuestion> getRandomQuestion() {
        return questionBankRepository.findRandomQuestion()
                .map(q -> new GeneratedQuestion(q.getQuestion(), q.getOptions(), q.getCorrectAnswer()));
    }

    /**
     * Returns a random QuestionBank entity, or empty if the bank is empty.
     */
    public Optional<QuestionBank> getRandomQuestionEntity() {
        return questionBankRepository.findRandomQuestion();
    }

    /**
     * Saves an AI-generated question (with answer) to the question bank.
     * If a question with the same normalized text already exists, returns the existing entity (no duplicate).
     */
    @Transactional
    public QuestionBank saveFromGenerated(GeneratedQuestion generated) {
        if (generated == null || generated.getQuestion() == null || generated.getQuestion().isBlank()) {
            return null;
        }
        String hash = QuestionBank.computeQuestionHash(generated.getQuestion());
        QuestionBank existing = questionBankRepository.findByQuestionHash(hash)
                .or(() -> questionBankRepository.findFirstByNormalizedQuestion(generated.getQuestion()))
                .orElse(null);
        if (existing != null) {
            if (existing.getQuestionHash() == null) {
                existing.setQuestionHash(hash);
                questionBankRepository.save(existing);
            }
            log.debug("Question already exists in question bank: id={}, skipping duplicate", existing.getId());
            return existing;
        }
        QuestionBank entity = new QuestionBank(
                generated.getQuestion(),
                generated.getOptions(),
                generated.getCorrectAnswer() != null ? generated.getCorrectAnswer() : "A"
        );
        entity = questionBankRepository.save(entity);
        log.info("Saved AI-generated question to question bank: id={}", entity.getId());
        return entity;
    }

    /**
     * Saves a batch of AI-generated questions to the question bank in one transaction.
     * Skips questions that already exist (by normalized text) and de-duplicates within the batch.
     *
     * @param generatedList list of generated questions (null/blank entries are skipped)
     * @return list of persisted entities (new only; existing duplicates are not included)
     */
    @Transactional
    public List<QuestionBank> saveAllFromGenerated(List<GeneratedQuestion> generatedList) {
        if (generatedList == null || generatedList.isEmpty()) {
            return List.of();
        }
        Set<String> seenHashesInBatch = new HashSet<>();
        List<QuestionBank> entities = new ArrayList<>();
        for (GeneratedQuestion g : generatedList) {
            if (g == null || g.getQuestion() == null || g.getQuestion().isBlank()) continue;
            String hash = QuestionBank.computeQuestionHash(g.getQuestion());
            if (seenHashesInBatch.contains(hash)) continue;
            if (questionBankRepository.findByQuestionHash(hash).isPresent()) continue;
            if (questionBankRepository.findFirstByNormalizedQuestion(g.getQuestion()).isPresent()) continue;
            seenHashesInBatch.add(hash);
            entities.add(new QuestionBank(
                    g.getQuestion(),
                    g.getOptions(),
                    g.getCorrectAnswer() != null ? g.getCorrectAnswer() : "A"
            ));
        }
        if (entities.isEmpty()) {
            log.info("Batch save: all questions were duplicates, nothing to save");
            return List.of();
        }
        List<QuestionBank> saved = questionBankRepository.saveAll(entities);
        log.info("Batch saved {} questions to question bank (duplicates skipped)", saved.size());
        return saved;
    }
}
