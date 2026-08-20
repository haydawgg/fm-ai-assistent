package com.github.fmaiassistent.repository;

import com.github.fmaiassistent.domain.entity.PlayerImportedStatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlayerImportedStatRepository extends JpaRepository<PlayerImportedStatEntity, Long> {
    void deleteBySourceAndSeasonKeyAndStatsScope(String source, String seasonKey, String statsScope);

    List<PlayerImportedStatEntity> findByPlayerIdAndSeasonKeyAndStatsScopeOrderByStatName(
            Long playerId, String seasonKey, String statsScope);
}
