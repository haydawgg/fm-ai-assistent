package com.github.fmaiassistent.service;

import com.github.fmaiassistent.repository.*;
import com.github.fmaiassistent.domain.entity.ClubEntity;
import com.github.fmaiassistent.domain.entity.CompetitionEntity;
import com.github.fmaiassistent.domain.entity.LoadMetadataEntity;
import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.linux.GameDateFinder;
import com.github.fmaiassistent.exporter.PlayerExporter;
import com.github.fmaiassistent.exporter.TacticExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import com.github.fmaiassistent.config.CaffeineCacheConfiguration;
import org.springframework.util.StopWatch;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class PlayerDatabaseService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerDatabaseService.class);

    private static final int INSERT_BATCH_SIZE = 500;
    private static final List<String> PLAYER_INSERT_COLUMNS = playerInsertColumns();
    private static final String PLAYER_INSERT_SQL = playerInsertSql(PLAYER_INSERT_COLUMNS);

    private final PlayerRepository players;
    private final ClubRepository clubRepository;
    private final CompetitionRepository competitionRepository;
    private final ClubDatabaseService clubDatabaseService;
    private final LoadMetadataRepository metadata;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final PlayerExporter exporter = new PlayerExporter();

    public PlayerDatabaseService(
            PlayerRepository players,
            ClubRepository clubRepository,
            CompetitionRepository competitionRepository,
            ClubDatabaseService clubDatabaseService,
            LoadMetadataRepository metadata,
            EntityManager entityManager,
            JdbcTemplate jdbcTemplate) {
        this.players = players;
        this.clubRepository = clubRepository;
        this.competitionRepository = competitionRepository;
        this.clubDatabaseService = clubDatabaseService;
        this.metadata = metadata;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public LoadResult loadAllPlayers(int pid, int build, Long gamePluginBase) throws IOException {
        clubDatabaseService.loadAllClubs(pid, build, gamePluginBase);
        return saveExported(exporter.exportAllPlayers(pid, build, gamePluginBase));
    }

    @Transactional
    @CacheEvict(cacheNames = CaffeineCacheConfiguration.PLAYER_FILTER_OPTIONS_CACHE, allEntries = true)
    public LoadResult saveExported(PlayerExporter.ExportResult result) {
        return saveExported(result, LoadProgressReporter.NONE);
    }

    @CacheEvict(cacheNames = CaffeineCacheConfiguration.PLAYER_FILTER_OPTIONS_CACHE, allEntries = true)
    public LoadResult saveExported(PlayerExporter.ExportResult result, Consumer<LoadProgress> progress) {
        LoadProgressReporter reporter = new LoadProgressReporter(progress);
        long total = result.rows().size();
        reporter.start(LoadProgress.Phase.SAVING, total);
        Map<Long, ClubEntity> clubsByAddress = clubsByAddress();
        List<PlayerEntity> batch = new ArrayList<>(INSERT_BATCH_SIZE);
        long saved = 0;
        for (Map<String, Object> row : result.rows()) {
            batch.add(playerEntity(row, clubsByAddress));
            if (batch.size() >= INSERT_BATCH_SIZE) {
                flushPlayerBatch(batch);
                saved += INSERT_BATCH_SIZE;
                reporter.report(new LoadProgress(LoadProgress.Phase.SAVING, saved, total, saved));
            }
        }
        int remaining = batch.size();
        flushPlayerBatch(batch);
        saved += remaining;
        finishSnapshot(result);
        reporter.finish(new LoadProgress(LoadProgress.Phase.SAVING, total, total, saved));
        return new LoadResult(result.gameDate(), (int) (result.kept() > 0 ? result.kept() : result.rows().size()));
    }

    public Map<Long, ClubEntity> clubLookup() {
        return clubsByAddress();
    }

    public void savePlayerChunk(List<Map<String, Object>> chunk, Map<Long, ClubEntity> clubsByAddress) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        List<PlayerEntity> batch = new ArrayList<>(chunk.size());
        for (Map<String, Object> row : chunk) {
            batch.add(playerEntity(row, clubsByAddress));
        }
        flushPlayerBatch(batch);
    }

    public void finishSnapshot(PlayerExporter.ExportResult result) {
        metadata.save(new LoadMetadataEntity("game_date", result.gameDate()));
        metadata.save(new LoadMetadataEntity("loaded_at", OffsetDateTime.now().toString()));
        metadata.save(new LoadMetadataEntity("game_build", String.valueOf(result.build())));
        metadata.save(new LoadMetadataEntity("season_key", seasonKey(result.gameDate())));
        metadata.save(new LoadMetadataEntity("season_stats_scope", "all_competitions"));
        metadata.save(new LoadMetadataEntity("season_stats_source", "native_memory"));
        metadata.save(new LoadMetadataEntity("season_stats_state", result.seasonStatsState()));
        metadata.save(new LoadMetadataEntity("season_stats_available", String.valueOf(result.seasonStatsAvailable())));
        metadata.save(new LoadMetadataEntity("season_stats_partial", String.valueOf(result.seasonStatsPartial())));
        metadata.save(new LoadMetadataEntity("season_stats_read_at", OffsetDateTime.now().toString()));
        if (result.pluginIdentity() != null) {
            metadata.save(new LoadMetadataEntity("game_plugin_path", result.pluginIdentity().path()));
            metadata.save(new LoadMetadataEntity("game_plugin_sha256", result.pluginIdentity().sha256()));
            metadata.save(new LoadMetadataEntity("game_plugin_size", String.valueOf(result.pluginIdentity().size())));
        }
        saveTactic(result);
    }

    private static String seasonKey(String gameDate) {
        if (gameDate == null || gameDate.isBlank()) {
            return "";
        }
        try {
            LocalDate date = LocalDate.parse(gameDate);
            int startYear = date.getMonthValue() >= 7 ? date.getYear() : date.getYear() - 1;
            return startYear + "/" + String.valueOf(startYear + 1).substring(2);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private void flushPlayerBatch(List<PlayerEntity> batch) {
        if (batch.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(PLAYER_INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws SQLException {
                PlayerEntity entity = batch.get(index);
                for (int column = 0; column < PLAYER_INSERT_COLUMNS.size(); column++) {
                    ps.setObject(column + 1, insertValue(entity, PLAYER_INSERT_COLUMNS.get(column)));
                }
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });
        entityManager.clear();
        batch.clear();
    }

    private static Object insertValue(PlayerEntity entity, String column) {
        if ("club_id".equals(column)) {
            return entity.getClubEntity() == null ? null : entity.getClubEntity().getId();
        }
        if ("playing_club_id".equals(column)) {
            return entity.getPlayingClubEntity() == null ? null : entity.getPlayingClubEntity().getId();
        }
        return entity.getColumnValue(column.toUpperCase(Locale.ROOT));
    }

    private static List<String> playerInsertColumns() {
        List<String> columns = new ArrayList<>(PlayerExporter.FIELD_NAMES.size() + 2);
        for (String field : PlayerExporter.FIELD_NAMES) {
            columns.add(PlayerColumnNames.toColumnName(field));
        }
        columns.add("club_id");
        columns.add("playing_club_id");
        return List.copyOf(columns);
    }

    private static String playerInsertSql(List<String> columns) {
        String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
        return "INSERT INTO players (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")";
    }

    private void saveTactic(PlayerExporter.ExportResult result) {
        TacticExporter.Snapshot tactic = result.tactic();
        if (tactic == null) {
            metadata.save(new LoadMetadataEntity("tactic_formation", ""));
            metadata.save(new LoadMetadataEntity("tactic_slots", ""));
            metadata.save(new LoadMetadataEntity("tactic_selected", ""));
            return;
        }
        Map<String, String> namesByRecord = new HashMap<>();
        Set<String> neededRecords = new HashSet<>();
        for (long person : tactic.selectedPersonAddresses()) {
            if (person != 0L) {
                neededRecords.add("0x" + Long.toHexString(person));
            }
        }
        if (!neededRecords.isEmpty()) {
            if (!result.rows().isEmpty()) {
                for (Map<String, Object> row : result.rows()) {
                    String record = String.valueOf(row.get("record"));
                    if (neededRecords.contains(record)) {
                        namesByRecord.put(record, String.valueOf(row.get("name")));
                        if (namesByRecord.size() == neededRecords.size()) {
                            break;
                        }
                    }
                }
            } else {
                for (PlayerEntity player : players.findByRecordAddressIn(neededRecords)) {
                    if (player.getRecordAddress() != null) {
                        namesByRecord.put(player.getRecordAddress(), player.getName());
                    }
                }
            }
        }
        List<String> selected = new ArrayList<>();
        for (int index = 0; index < tactic.positions().size(); index++) {
            String position = tactic.positions().get(index);
            long person = index < tactic.selectedPersonAddresses().size()
                    ? tactic.selectedPersonAddresses().get(index)
                    : 0L;
            String record = person == 0 ? "" : "0x" + Long.toHexString(person);
            String name = namesByRecord.getOrDefault(record, "");
            selected.add(position + "," + name);
        }
        metadata.save(new LoadMetadataEntity("tactic_formation", tactic.formation()));
        metadata.save(new LoadMetadataEntity("tactic_slots", tactic.slotText()));
        metadata.save(new LoadMetadataEntity("tactic_selected", String.join("\n", selected)));
    }

    private static PlayerEntity playerEntity(Map<String, Object> row, Map<Long, ClubEntity> clubsByAddress) {
        PlayerEntity entity = PlayerEntity.fromExportRow(row);
        Object clubAddress = row.get("_club_address");
        if (clubAddress instanceof Number number) {
            entity.setClubEntity(clubsByAddress.get(number.longValue()));
        }
        Object playingClubAddress = row.get("_playing_club_address");
        if (playingClubAddress instanceof Number number) {
            entity.setPlayingClubEntity(clubsByAddress.get(number.longValue()));
        }
        return entity;
    }

    private Map<Long, ClubEntity> clubsByAddress() {
        Map<Long, ClubEntity> out = new HashMap<>();
        for (ClubEntity club : clubRepository.findAll()) {
            if (club.getSourceAddress() == null) {
                continue;
            }
            out.merge(club.getSourceAddress(), club, PlayerDatabaseService::higherReputation);
        }
        return out;
    }

    private static ClubEntity higherReputation(ClubEntity left, ClubEntity right) {
        int leftReputation = left.getReputation() == null ? 0 : left.getReputation();
        int rightReputation = right.getReputation() == null ? 0 : right.getReputation();
        return rightReputation > leftReputation ? right : left;
    }

    @Transactional(readOnly = true)
    public long countPlayers() {
        return players.count();
    }

    @Transactional(readOnly = true)
    public List<PlayerEntity> findAllPlayerEntities() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        List<PlayerEntity> out = players.findAllWithClubs();
        stopWatch.stop();
        LOGGER.info("Time to get player entities: {}", stopWatch.getTotalTime(TimeUnit.MILLISECONDS));
        return out;
    }

    @Transactional(readOnly = true)
    public List<PlayerEntity> findPlayerEntities(PlayerFilterCriteria filter) {
        PlayerFilterCriteria safeFilter = filter == null ? PlayerFilterCriteria.empty() : filter;
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Specification<PlayerEntity> spec = PlayerSpecifications.fromFilter(safeFilter);
        List<PlayerEntity> out = players.findAll(spec);
        boolean needsAgeFilter = safeFilter.ageMin() != null || safeFilter.ageMax() != null;
        boolean needsDateFilter = safeFilter.contractEndDateFrom() != null || safeFilter.contractEndDateTo() != null;
        if (needsAgeFilter || needsDateFilter) {
            out = out.stream().filter(player -> {
                if (needsAgeFilter && !inRange(GameDateFinder.effectiveAge(
                        player.getAge(), player.getDateOfBirth(), player.getAgeAsOf()),
                        safeFilter.ageMin(), safeFilter.ageMax())) {
                    return false;
                }
                return !needsDateFilter || dateInRange(player.getContractEndDate(), safeFilter.contractEndDateFrom(), safeFilter.contractEndDateTo());
            }).toList();
        }
        stopWatch.stop();
        LOGGER.info("Time to get filtered player entities: {}", stopWatch.getTotalTime(TimeUnit.MILLISECONDS));
        return out;
    }

    /** Loads one server-side page for the player desk. Unknown persisted scalar values fail range filters. */
    @Transactional(readOnly = true)
    public List<PlayerEntity> findPlayerPage(PlayerFilterCriteria filter, int offset, int limit) {
        return findPlayerPage(filter, offset, limit, null, false);
    }

    @Transactional(readOnly = true)
    public List<PlayerEntity> findPlayerPage(
            PlayerFilterCriteria filter, int offset, int limit, String sortKey, boolean descending) {
        PlayerFilterCriteria safeFilter = filter == null ? PlayerFilterCriteria.empty() : filter;
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(500, limit));
        boolean legacyJavaFilters = false;
        if (legacyJavaFilters) {
            return page(findPlayerEntities(safeFilter), safeOffset, safeLimit);
        }
        String sortField = sortableEntityField(sortKey);
        Sort.Direction direction = descending ? Sort.Direction.DESC : Sort.Direction.ASC;
        Page<PlayerEntity> page = players.findAll(PlayerSpecifications.fromFilter(safeFilter),
                PageRequest.of(safeOffset / safeLimit, safeLimit,
                        Sort.by(new Sort.Order(direction, sortField), new Sort.Order(Sort.Direction.ASC, "id"))));
        return List.copyOf(page.getContent());
    }

    private static String sortableEntityField(String sortKey) {
        if (sortKey == null || sortKey.isBlank()) return "name";
        String normalized = sortKey.trim().toLowerCase(Locale.ROOT);
        if ("age".equals(normalized)) {
            return "ageNumeric";
        }
        return Set.of("name", "age", "height_cm", "nationality", "club", "playing_club", "ca", "pa",
                        "appearances", "starts", "minutes", "goals", "assists", "average_rating",
                        "salary_weekly_raw", "asking_price", "contract_end_date", "transfer_listed",
                        "listed_for_loan", "transfer_agreed", "injured", "current_reputation",
                        "home_reputation", "world_reputation")
                .contains(normalized)
                ? PlayerColumnNames.toEntityFieldName(normalized)
                : "name";
    }

    @Transactional(readOnly = true)
    public long countPlayerEntities(PlayerFilterCriteria filter) {
        PlayerFilterCriteria safeFilter = filter == null ? PlayerFilterCriteria.empty() : filter;
        boolean legacyJavaFilters = false;
        if (legacyJavaFilters) {
            return findPlayerEntities(safeFilter).size();
        }
        return players.count(PlayerSpecifications.fromFilter(safeFilter));
    }

    private static List<PlayerEntity> page(List<PlayerEntity> rows, int offset, int limit) {
        if (offset >= rows.size()) return List.of();
        return rows.subList(offset, Math.min(rows.size(), offset + limit));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CaffeineCacheConfiguration.PLAYER_FILTER_OPTIONS_CACHE, key = "'playingNations'")
    public List<String> findPlayingNations() {
        return competitionRepository.findDistinctNations();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CaffeineCacheConfiguration.PLAYER_FILTER_OPTIONS_CACHE, key = "'playingCompetitions'")
    public List<String> findPlayingCompetitions() {
        return competitionRepository.findDistinctNameByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CaffeineCacheConfiguration.PLAYER_FILTER_OPTIONS_CACHE, key = "'clubs'")
    public List<String> findClubs() {
        return clubRepository.findDistinctNameByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CaffeineCacheConfiguration.PLAYER_FILTER_OPTIONS_CACHE, key = "'nationalities'")
    public List<String> findNationalities() {
        return players.findDistinctNationalities();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> metadata() {
        Map<String, Object> out = new LinkedHashMap<>();
        metadata.findAll(Sort.by("key")).forEach(row -> out.put(row.getKey(), row.getValue()));
        out.put("count", countPlayers());
        return out;
    }

    static boolean matchesPlayerFilter(PlayerEntity player, PlayerFilterCriteria filter) {
        PlayerFilterCriteria.Advanced advanced = filter.advanced();
        return contains(player.getName(), filter.name())
                && equalsIgnoreCase(player.getGender(), filter.gender())
                && equalsIgnoreCase(Optional.ofNullable(player.getPlayingClubEntity()).map(ClubEntity::getCompetitionEntity).map(CompetitionEntity::getNation).orElse(null), filter.playingNation())
                && equalsIgnoreCase(Optional.ofNullable(player.getPlayingClubEntity()).map(ClubEntity::getCompetitionEntity).map(CompetitionEntity::getName).orElse(null), filter.playingCompetition())
                && matchesClub(player, filter.club(), advanced.clubScope())
                && inRange(player.getSalaryWeeklyRaw() == null ? null : player.getSalaryWeeklyRaw().longValue(),
                        null, filter.salaryMax())
                && equalsIgnoreCase(player.getNationality(), filter.nationality())
                && inRange(GameDateFinder.effectiveAge(player.getAge(), player.getDateOfBirth(), player.getAgeAsOf()),
                        filter.ageMin(), filter.ageMax())
                && inRange(player.getHeightCm(), filter.heightMin(), filter.heightMax())
                && inRange(player.getCurrentReputation(), filter.currentReputationMin(), filter.currentReputationMax())
                && inRange(player.getHomeReputation(), filter.homeReputationMin(), filter.homeReputationMax())
                && inRange(player.getWorldReputation(), filter.worldReputationMin(), filter.worldReputationMax())
                && inRange(player.getCa(), filter.caMin(), filter.caMax())
                && inRange(player.getPa(), filter.paMin(), filter.paMax())
                && inRange(player.getAskingPrice(), filter.askingPriceMin(), filter.askingPriceMax())
                && dateInRange(player.getContractEndDate(), filter.contractEndDateFrom(), filter.contractEndDateTo())
                && matchesBoolean(player.getInjured(), advanced.injured())
                && matchesBoolean(player.getTransferListed(), advanced.transferListed())
                && matchesBoolean(player.getListedForLoan(), advanced.listedForLoan())
                && matchesBoolean(player.getTransferAgreed(), advanced.transferAgreed())
                && matchesFreeAgent(player, advanced.freeAgent())
                && matchesLoan(player, advanced.loanStatus())
                && inRange(player.getAppearances(), advanced.appearancesMin(), advanced.appearancesMax())
                && inRange(player.getStarts(), advanced.startsMin(), advanced.startsMax())
                && inRange(player.getMinutes(), advanced.minutesMin(), advanced.minutesMax())
                && inRange(player.getGoals(), advanced.goalsMin(), advanced.goalsMax())
                && inRange(player.getAssists(), advanced.assistsMin(), advanced.assistsMax())
                && inRange(player.getAverageRating(), advanced.averageRatingMin(), advanced.averageRatingMax())
                && minimumsMatch(player, filter.positionMinimums())
                && minimumsMatch(player, filter.attributeMinimums());
    }

    private static boolean contains(Object value, String term) {
        return term == null || term.isBlank()
                || String.valueOf(value == null ? "" : value).toLowerCase(Locale.ROOT)
                .contains(term.toLowerCase(Locale.ROOT).trim());
    }

    private static boolean equalsIgnoreCase(Object value, String term) {
        return term == null || term.isBlank()
                || String.valueOf(value == null ? "" : value).equalsIgnoreCase(term.trim());
    }

    private static boolean matchesClub(PlayerEntity player, String club, PlayerFilterCriteria.ClubScope scope) {
        if (scope == PlayerFilterCriteria.ClubScope.CONTRACTED) {
            return equalsIgnoreCase(Optional.ofNullable(player.getClubEntity()).map(ClubEntity::getName).orElse(null), club)
                    || equalsIgnoreCase(player.getClub(), club);
        }
        if (scope == PlayerFilterCriteria.ClubScope.PLAYING) {
            return equalsIgnoreCase(Optional.ofNullable(player.getPlayingClubEntity()).map(ClubEntity::getName).orElse(null), club)
                    || equalsIgnoreCase(player.getPlayingClub(), club);
        }
        return equalsIgnoreCase(Optional.ofNullable(player.getClubEntity()).map(ClubEntity::getName).orElse(null), club)
                || equalsIgnoreCase(Optional.ofNullable(player.getPlayingClubEntity()).map(ClubEntity::getName).orElse(null), club)
                || equalsIgnoreCase(player.getClub(), club)
                || equalsIgnoreCase(player.getPlayingClub(), club);
    }

    private static boolean matchesFreeAgent(PlayerEntity player, Boolean expected) {
        if (expected == null) return true;
        boolean freeAgent = player.getClub() == null || player.getClub().isBlank();
        return expected == freeAgent;
    }

    private static boolean matchesLoan(PlayerEntity player, PlayerFilterCriteria.LoanStatus status) {
        if (status == null || status == PlayerFilterCriteria.LoanStatus.ANY) return true;
        boolean loaned = "yes".equalsIgnoreCase(player.getIsLoanedOut());
        return status == PlayerFilterCriteria.LoanStatus.LOANED ? loaned : !loaned;
    }

    private static boolean minimumsMatch(PlayerEntity player, Map<String, Integer> minimums) {
        for (Map.Entry<String, Integer> minimum : minimums.entrySet()) {
            Integer value = asInt(player.getColumnValue(minimum.getKey()));
            if (value == null || value < minimum.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static boolean inRange(Integer value, Integer min, Integer max) {
        if (min == null && max == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return (min == null || value >= min) && (max == null || value <= max);
    }

    private static boolean inRange(Long value, Long min, Long max) {
        if (min == null && max == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return (min == null || value >= min) && (max == null || value <= max);
    }

    private static boolean inRange(Double value, Double min, Double max) {
        if (min == null && max == null) return true;
        if (value == null) return false;
        return (min == null || value >= min) && (max == null || value <= max);
    }

    private static boolean matchesBoolean(Boolean value, Boolean expected) {
        return expected == null || (value != null && value.equals(expected));
    }

    private static boolean dateInRange(Object value, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return false;
        }
        try {
            LocalDate date = LocalDate.parse(String.valueOf(value));
            return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static Integer asInt(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    public record LoadResult(String gameDate, int count) {
    }
}
