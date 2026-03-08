package com.tech.techkuppiapp.service;

import com.tech.techkuppiapp.dto.GeneratedQuestion;
import com.tech.techkuppiapp.entity.QuestionBank;
import com.tech.techkuppiapp.repository.QuestionBankRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuestionBankService {

    private static final Logger log = LoggerFactory.getLogger(QuestionBankService.class);

    private final QuestionBankRepository questionBankRepository;

    public QuestionBankService(QuestionBankRepository questionBankRepository) {
        this.questionBankRepository = questionBankRepository;
    }

    /**
     * Saves an AI-generated question (with answer) to the question bank.
     */
    @Transactional
    public QuestionBank saveFromGenerated(GeneratedQuestion generated) {
        if (generated == null || generated.getQuestion() == null || generated.getQuestion().isBlank()) {
            return null;
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
     *
     * @param generatedList list of generated questions (null/blank entries are skipped)
     * @return list of persisted entities
     */
    @Transactional
    public List<QuestionBank> saveAllFromGenerated(List<GeneratedQuestion> generatedList) {
        if (generatedList == null || generatedList.isEmpty()) {
            return List.of();
        }
        List<QuestionBank> entities = new ArrayList<>();
        for (GeneratedQuestion g : generatedList) {
            if (g == null || g.getQuestion() == null || g.getQuestion().isBlank()) continue;
            entities.add(new QuestionBank(
                    g.getQuestion(),
                    g.getOptions(),
                    g.getCorrectAnswer() != null ? g.getCorrectAnswer() : "A"
            ));
        }
        if (entities.isEmpty()) {
            return List.of();
        }
        List<QuestionBank> saved = questionBankRepository.saveAll(entities);
        log.info("Batch saved {} questions to question bank", saved.size());
        return saved;
    }
}
