package com.github.fmaiassistent.repository;

import com.github.fmaiassistent.domain.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    List<ChatMessageEntity> findBySessionIdOrderByOrdinalAsc(String sessionId);

    void deleteBySessionIdAndOrdinalGreaterThanEqual(String sessionId, int ordinal);

    void deleteBySessionId(String sessionId);

    long countBySessionId(String sessionId);

    ChatMessageEntity findBySessionIdAndOrdinal(String sessionId, int ordinal);

    @Query("select coalesce(sum(m.costUsd), 0) from ChatMessageEntity m where m.createdAt >= :from and m.costUsd is not null")
    double sumCostUsdSince(@Param("from") OffsetDateTime from);
}
