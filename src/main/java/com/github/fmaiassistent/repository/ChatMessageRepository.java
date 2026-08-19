package com.github.fmaiassistent.repository;

import com.github.fmaiassistent.domain.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    List<ChatMessageEntity> findBySessionIdOrderByOrdinalAsc(String sessionId);

    List<ChatMessageEntity> findBySessionIdOrderByOrdinalAsc(String sessionId, Pageable pageable);

    void deleteBySessionIdAndOrdinalGreaterThanEqual(String sessionId, int ordinal);

    long deleteBySessionIdAndOrdinalLessThan(String sessionId, int ordinal);

    void deleteBySessionId(String sessionId);

    long countBySessionId(String sessionId);

    @Query("select coalesce(max(m.ordinal), -1) from ChatMessageEntity m where m.sessionId = :sessionId")
    int maxOrdinalBySessionId(@Param("sessionId") String sessionId);

    ChatMessageEntity findBySessionIdAndOrdinal(String sessionId, int ordinal);

    @Query("select coalesce(sum(m.costUsd), 0) from ChatMessageEntity m where m.createdAt >= :from and m.costUsd is not null")
    BigDecimal sumCostUsdSince(@Param("from") OffsetDateTime from);
}
