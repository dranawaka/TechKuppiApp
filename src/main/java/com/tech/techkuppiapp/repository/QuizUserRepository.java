package com.tech.techkuppiapp.repository;

import com.tech.techkuppiapp.entity.QuizUser;

import java.util.Optional;

public interface QuizUserRepository extends org.springframework.data.jpa.repository.JpaRepository<QuizUser, Long> {

    Optional<QuizUser> findByTelegramUserId(Long telegramUserId);
}
