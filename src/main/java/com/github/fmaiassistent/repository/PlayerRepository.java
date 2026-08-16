package com.github.fmaiassistent.repository;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PlayerRepository extends JpaRepository<PlayerEntity, Long>, JpaSpecificationExecutor<PlayerEntity> {

    @EntityGraph(attributePaths = {"clubEntity", "clubEntity.competitionEntity", "playingClubEntity"})
    List<PlayerEntity> findAll(Specification<PlayerEntity> spec);

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
            where player.club in :clubVariants
               or player.playingClub in :clubVariants
               or (c is not null and lower(c.name) = lower(:club))
               or (player.playingClubEntity is not null and lower(player.playingClubEntity.name) = lower(:club))
            """)
    List<PlayerEntity> findAllWithClubsByClubName(@Param("club") String club, @Param("clubVariants") Collection<String> clubVariants);

    List<PlayerEntity> findByRecordAddressIn(Collection<String> recordAddresses);

    @Query("select distinct p.nationality from PlayerEntity p where p.nationality is not null and p.nationality <> '' order by p.nationality")
    List<String> findDistinctNationalities();
}
