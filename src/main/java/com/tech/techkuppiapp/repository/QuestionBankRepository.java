package com.tech.techkuppiapp.repository;

import com.tech.techkuppiapp.entity.QuestionBank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, Long> {

    /** Find by question hash (from QuestionBank.computeQuestionHash) to detect duplicates. */
    Optional<QuestionBank> findByQuestionHash(String questionHash);

    /** Find by normalized question text (catches duplicates when question_hash is null on existing rows). */
    @Query("SELECT q FROM QuestionBank q WHERE LOWER(TRIM(q.question)) = LOWER(TRIM(:question))")
    Optional<QuestionBank> findFirstByNormalizedQuestion(@Param("question") String question);

    /** Return one random question (for question-bank source). PostgreSQL RANDOM(). */
    @Query(value = "SELECT * FROM question_bank ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<QuestionBank> findRandomQuestion();
}
