package com.github.fmaiassistent.repository;

import com.github.fmaiassistent.domain.entity.PlayerStatsImportHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlayerStatsImportHistoryRepository extends JpaRepository<PlayerStatsImportHistoryEntity, Long> {
    List<PlayerStatsImportHistoryEntity> findTop20ByOrderByImportedAtDesc();
}
