package com.github.fmaiassistent.service;

import com.github.fmaiassistent.repository.*;
import com.github.fmaiassistent.domain.entity.ClubEntity;
import com.github.fmaiassistent.domain.entity.CompetitionEntity;
import com.github.fmaiassistent.domain.entity.LoadMetadataEntity;
import com.github.fmaiassistent.exporter.ClubExporter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class ClubDatabaseService {
    private static final List<String> CLUB_INSERT_COLUMNS = List.of(
            "source_address", "name", "gender", "competition", "competition_id",
            "reputation", "nation", "balance", "transfer_budget", "payroll_budget");
    private static final String CLUB_INSERT_SQL = clubInsertSql();

    private final ClubRepository clubs;
    private final CompetitionRepository competitions;
    private final CompetitionDatabaseService competitionDatabaseService;
    private final LoadMetadataRepository metadata;
    private final JdbcTemplate jdbcTemplate;
    private final ClubExporter exporter = new ClubExporter();

    public ClubDatabaseService(
            ClubRepository clubs,
            CompetitionRepository competitions,
            CompetitionDatabaseService competitionDatabaseService,
            LoadMetadataRepository metadata,
            JdbcTemplate jdbcTemplate) {
        this.clubs = clubs;
        this.competitions = competitions;
        this.competitionDatabaseService = competitionDatabaseService;
        this.metadata = metadata;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public LoadResult loadAllClubs(int pid, int build, Long gamePluginBase) throws IOException {
        competitionDatabaseService.loadAllCompetitions(pid, build, gamePluginBase);
        return saveExported(exporter.exportAllClubs(pid, build, gamePluginBase));
    }

    @Transactional
    public LoadResult saveExported(ClubExporter.ExportResult result) {
        Map<Long, CompetitionEntity> competitionsByAddress = competitionsByAddress();
        List<ClubEntity> batch = new ArrayList<>(result.rows().size());
        for (Map<String, Object> row : result.rows()) {
            batch.add(clubEntity(row, competitionsByAddress));
        }
        insertClubs(batch);
        metadata.save(new LoadMetadataEntity("clubs_loaded_at", OffsetDateTime.now().toString()));
        return new LoadResult(result.rows().size());
    }

    private void insertClubs(List<ClubEntity> batch) {
        if (batch.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(CLUB_INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws SQLException {
                ClubEntity club = batch.get(index);
                ps.setObject(1, club.getSourceAddress());
                ps.setObject(2, club.getName());
                ps.setObject(3, club.getGender());
                ps.setObject(4, club.getCompetition());
                ps.setObject(5, club.getCompetitionEntity() == null ? null : club.getCompetitionEntity().getId());
                ps.setObject(6, club.getReputation());
                ps.setObject(7, club.getNation());
                ps.setObject(8, club.getBalance());
                ps.setObject(9, club.getTransferBudget());
                ps.setObject(10, club.getPayrollBudget());
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });
    }

    private static String clubInsertSql() {
        String placeholders = String.join(", ", Collections.nCopies(CLUB_INSERT_COLUMNS.size(), "?"));
        return "INSERT INTO clubs (" + String.join(", ", CLUB_INSERT_COLUMNS) + ") VALUES (" + placeholders + ")";
    }

    private static ClubEntity clubEntity(Map<String, Object> row, Map<Long, CompetitionEntity> competitionsByAddress) {
        ClubEntity entity = ClubEntity.fromExportRow(row);
        Object address = row.get("_competition_address");
        if (address instanceof Number number) {
            entity.setCompetitionEntity(competitionsByAddress.get(number.longValue()));
        }
        return entity;
    }

    private Map<Long, CompetitionEntity> competitionsByAddress() {
        Map<Long, CompetitionEntity> out = new HashMap<>();
        for (CompetitionEntity competition : competitions.findAll()) {
            if (competition.getSourceAddress() != null) {
                out.put(competition.getSourceAddress(), competition);
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public long countClubs() {
        return clubs.count();
    }

    @Transactional(readOnly = true)
    public boolean hasClubs() {
        return clubs.count() > 0;
    }

    @Transactional(readOnly = true)
    public List<String> findNames() {
        return clubs.findDistinctNameByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<String> findCompetitionNames() {
        return clubs.findDistinctCompetitionByOrderByCompetitionAsc();
    }

    @Transactional(readOnly = true)
    public List<String> findNations() {
        return clubs.findDistinctNationByOrderByNationAsc();
    }

    @Transactional(readOnly = true)
    public List<ClubEntity> findAllClubs() {
        return clubs.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Transactional(readOnly = true)
    public List<ClubEntity> findClubEntities(ClubFilterCriteria filter) {
        ClubFilterCriteria safeFilter = filter == null ? ClubFilterCriteria.empty() : filter;
        if (safeFilter.isEmpty()) {
            return findAllClubs();
        }
        return clubs.findAll(matching(safeFilter), Sort.by(Sort.Direction.ASC, "name"));
    }

    @Transactional(readOnly = true)
    public ClubEntity requireNamed(String clubName) {
        if (clubName == null || clubName.isBlank()) {
            throw new IllegalArgumentException("club not found: " + clubName);
        }
        String trimmed = clubName.trim();
        return highestReputation(clubs.findByNameIgnoreCase(trimmed))
                .or(() -> highestReputation(clubs.findByNameContainingIgnoreCase(trimmed)))
                .orElseThrow(() -> new IllegalArgumentException("club not found: " + clubName));
    }

    static Specification<ClubEntity> matching(ClubFilterCriteria filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            equalIgnoreCase(predicates, builder, root.get("name"), filter.name());
            equalIgnoreCase(predicates, builder, root.get("competition"), filter.competition());
            equalIgnoreCase(predicates, builder, root.get("nation"), filter.nation());
            inRange(predicates, builder, root.get("reputation"), filter.reputationMin(), filter.reputationMax());
            inRange(predicates, builder, root.get("balance"), filter.balanceMin(), filter.balanceMax());
            inRange(predicates, builder, root.get("transferBudget"), filter.transferBudgetMin(), filter.transferBudgetMax());
            inRange(predicates, builder, root.get("payrollBudget"), filter.payrollBudgetMin(), filter.payrollBudgetMax());
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void equalIgnoreCase(
            List<Predicate> predicates, CriteriaBuilder builder, Path<String> path, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        predicates.add(builder.equal(builder.lower(path), value.trim().toLowerCase(Locale.ROOT)));
    }

    private static void inRange(
            List<Predicate> predicates, CriteriaBuilder builder, Path<? extends Number> path, Number min, Number max) {
        if (min != null) {
            predicates.add(builder.ge(path.as(Double.class), min.doubleValue()));
        }
        if (max != null) {
            predicates.add(builder.le(path.as(Double.class), max.doubleValue()));
        }
    }

    private static Optional<ClubEntity> highestReputation(List<ClubEntity> matches) {
        return matches.stream()
                .max(Comparator.comparingInt(club -> {
                    Integer reputation = club.getReputation();
                    return reputation == null ? 0 : reputation;
                }));
    }

    @Transactional(readOnly = true)
    public List<ClubEntity> searchClubs(
            String nameContains, String nation, String competition, Integer reputationMin, int limit) {
        int safeLimit = Math.max(1, limit);
        Specification<ClubEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (nameContains != null && !nameContains.isBlank()) {
                predicates.add(builder.like(
                        builder.lower(root.get("name")),
                        "%" + nameContains.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            equalIgnoreCase(predicates, builder, root.get("nation"), nation);
            equalIgnoreCase(predicates, builder, root.get("competition"), competition);
            if (reputationMin != null) {
                predicates.add(builder.ge(root.get("reputation"), reputationMin));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return clubs.findAll(
                specification,
                PageRequest.of(
                        0,
                        safeLimit,
                        Sort.by(Sort.Direction.DESC, "reputation").and(Sort.by(Sort.Direction.ASC, "name"))))
                .getContent();
    }

    public record LoadResult(int count) {
    }
}
