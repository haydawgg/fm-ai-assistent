package com.github.fmaiassistent.repository;

import com.github.fmaiassistent.domain.entity.ChatSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {
    List<ChatSessionEntity> findAllByOrderByUpdatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChatSessionEntity s where s.id = :id")
    Optional<ChatSessionEntity> findByIdForUpdate(@Param("id") String id);

    @Query(value = """
            select distinct s.* from chat_session s
            left join chat_message m on m.session_id = s.id
            where lower(s.title) like lower(concat('%', :query, '%'))
               or lower(substring(m.body, 1, 4096)) like lower(concat('%', :query, '%'))
            order by s.updated_at desc
            """, nativeQuery = true)
    List<ChatSessionEntity> search(@Param("query") String query);
}
