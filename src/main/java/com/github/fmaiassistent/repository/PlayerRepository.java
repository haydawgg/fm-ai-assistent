package com.github.fmaiassistent.repository;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PlayerRepository extends JpaRepository<PlayerEntity, Long>, JpaSpecificationExecutor<PlayerEntity> {

    @Query("""
            select player
            from PlayerEntity player
            left join fetch player.clubEntity c
            left join fetch player.playingClubEntity
            left join fetch c.competitionEntity
            """)
    List<PlayerEntity> findAllWithClubs();

    @Query("""
            select distinct player
            from PlayerEntity player
            left join fetch player.clubEntity c
            left join fetch player.playingClubEntity
            left join fetch c.competitionEntity
            where lower(player.club) = lower(:club)
               or lower(player.playingClub) = lower(:club)
               or (c is not null and lower(c.name) = lower(:club))
               or (player.playingClubEntity is not null and lower(player.playingClubEntity.name) = lower(:club))
            """)
    List<PlayerEntity> findAllWithClubsByClubName(@Param("club") String club);

    List<PlayerEntity> findByRecordAddressIn(Collection<String> recordAddresses);
}
