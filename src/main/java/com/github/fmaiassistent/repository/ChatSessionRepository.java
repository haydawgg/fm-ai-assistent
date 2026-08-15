package com.github.fmaiassistent.repository;

import com.github.fmaiassistent.domain.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {
    List<ChatSessionEntity> findAllByOrderByUpdatedAtDesc();
}
