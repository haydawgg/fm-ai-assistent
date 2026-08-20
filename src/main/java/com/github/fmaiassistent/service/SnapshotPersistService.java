package com.github.fmaiassistent.service;

import com.github.fmaiassistent.config.CaffeineCacheConfiguration;
import com.github.fmaiassistent.domain.entity.ClubEntity;
import com.github.fmaiassistent.exporter.ClubExporter;
import com.github.fmaiassistent.exporter.CompetitionExporter;
import com.github.fmaiassistent.exporter.PlayerExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StopWatch;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.UUID;

@Service
public class SnapshotPersistService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotPersistService.class);
    private final CompetitionDatabaseService competitions;
    private final ClubDatabaseService clubs;
    private final PlayerDatabaseService players;
    private final SnapshotPublicationPort publication;
    private final CacheManager cacheManager;

    public SnapshotPersistService(
            CompetitionDatabaseService competitions,
            ClubDatabaseService clubs,
            PlayerDatabaseService players,
            SnapshotPublicationPort publication,
            CacheManager cacheManager) {
        this.competitions = competitions;
        this.clubs = clubs;
        this.players = players;
        this.publication = publication;
        this.cacheManager = cacheManager;
    }

    @FunctionalInterface
    public interface PlayerChunkExporter {
        PlayerExporter.ExportResult export(Consumer<List<Map<String, Object>>> chunkSink) throws IOException;
    }

    @Transactional
    public synchronized DatabaseLoadAllService.LoadAllResult persist(
            int pid,
            CompetitionExporter.ExportResult competitionRows,
            ClubExporter.ExportResult clubRows,
            PlayerExporter.ExportResult playerRows,
            Consumer<LoadProgress> progress) {
        try {
            return persist(pid, competitionRows, clubRows, progress, ignored -> playerRows);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Transactional
    public synchronized DatabaseLoadAllService.LoadAllResult persist(
            int pid,
            CompetitionExporter.ExportResult competitionRows,
            ClubExporter.ExportResult clubRows,
            Consumer<LoadProgress> progress,
            PlayerChunkExporter playerExport) throws IOException {
        LoadProgressReporter reporter = new LoadProgressReporter(progress);
        reporter.start(LoadProgress.Phase.SAVING, 1);
        StopWatch persistWatch = new StopWatch("persist");
        Map<Long, ClubEntity> clubsByAddress = new HashMap<>();
        AtomicLong saved = new AtomicLong();
        boolean[] snapshotReplaced = {false};
        String snapshotId = UUID.randomUUID().toString();
        registerCacheEvictionAfterCommit();
        persistWatch.start("players-export");
        PlayerExporter.ExportResult playerRows = playerExport.export(chunk -> {
            if (!snapshotReplaced[0]) {
                if (persistWatch.isRunning()) {
                    persistWatch.stop();
                }
                persistWatch.start("clear");
                publication.stage(snapshotId);
                persistWatch.stop();
                persistWatch.start("competitions");
                competitions.saveExported(competitionRows);
                persistWatch.stop();
                reporter.report(new LoadProgress(LoadProgress.Phase.SAVING, 0, 1, 0, " · competitions saved"));
                persistWatch.start("clubs");
                clubs.saveExported(clubRows);
                persistWatch.stop();
                reporter.report(new LoadProgress(LoadProgress.Phase.SAVING, 0, 1, 0, " · clubs saved"));
                clubsByAddress.putAll(players.clubLookup());
                snapshotReplaced[0] = true;
                persistWatch.start("players-export");
            }
            players.savePlayerChunk(chunk, clubsByAddress);
            saved.addAndGet(chunk.size());
        });
        if (persistWatch.isRunning()) {
            persistWatch.stop();
        }
        persistWatch.start("finish-snapshot");
        if (playerRows.kept() <= 0 && playerRows.rows().isEmpty()) {
            throw new IllegalStateException(
                    "RAM export found no players. Previous snapshot was not replaced. Is FM26 running with a save loaded?");
        }
        if (!snapshotReplaced[0]) {
            publication.stage(snapshotId);
            competitions.saveExported(competitionRows);
            clubs.saveExported(clubRows);
            snapshotReplaced[0] = true;
        }
        long savedCount;
        if (!playerRows.rows().isEmpty() && saved.get() == 0) {
            savedCount = players.saveExported(playerRows, progress).count();
        } else {
            players.finishSnapshot(playerRows);
            savedCount = saved.get();
        }
        publication.publish(snapshotId);
        persistWatch.stop();
        LOGGER.info("RAM persist timings:\n{}", persistWatch.prettyPrint());
        PlayerExporter.SkipSnapshot skips = playerRows.skips() == null
                ? PlayerExporter.SkipSnapshot.EMPTY
                : playerRows.skips();
        reporter.finish(new LoadProgress(
                LoadProgress.Phase.SAVING, savedCount, savedCount, savedCount, skips.toastFragment()));
        return new DatabaseLoadAllService.LoadAllResult(
                pid,
                playerRows.gameDate(),
                savedCount,
                clubs.countClubs(),
                competitions.countCompetitions(),
                skips.toastFragment());
    }

    private void registerCacheEvictionAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Snapshot persistence requires an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String cacheName : List.of(
                        CaffeineCacheConfiguration.PLAYERS_CACHE,
                        CaffeineCacheConfiguration.PLAYERS_WITH_CLUBS_CACHE,
                        CaffeineCacheConfiguration.NATIONS_CACHE,
                        CaffeineCacheConfiguration.COMPETITIONS_CACHE,
                        CaffeineCacheConfiguration.CLUB_NAMES_CACHE,
                        CaffeineCacheConfiguration.CLUB_CACHE,
                        CaffeineCacheConfiguration.PLAYER_MAPPING_CACHE,
                        CaffeineCacheConfiguration.PLAYER_FILTER_OPTIONS_CACHE)) {
                    Cache cache = cacheManager.getCache(cacheName);
                    if (cache != null) {
                        cache.clear();
                    }
                }
            }
        });
    }
}
