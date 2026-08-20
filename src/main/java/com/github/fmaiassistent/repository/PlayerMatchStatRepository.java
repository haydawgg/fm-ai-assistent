package com.github.fmaiassistent.repository;

import com.github.fmaiassistent.domain.entity.PlayerMatchStatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlayerMatchStatRepository extends JpaRepository<PlayerMatchStatEntity, Long> {
    void deleteBySourceAndSeasonKey(String source, String seasonKey);

    List<PlayerMatchStatEntity> findByPlayerIdAndSeasonKeyOrderByMatchDateDesc(
            Long playerId, String seasonKey);
}
