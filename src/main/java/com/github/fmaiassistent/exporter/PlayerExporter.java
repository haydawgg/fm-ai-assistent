package com.github.fmaiassistent.exporter;

import com.github.fmaiassistent.linux.FmMemoryStrings;
import com.github.fmaiassistent.linux.FmOffsets;
import com.github.fmaiassistent.linux.GamePluginIdentity;
import com.github.fmaiassistent.memory.ProcessMemoryReader;
import com.github.fmaiassistent.memory.ProcessReaders;
import com.github.fmaiassistent.player.AttributeDefinitions;
import com.github.fmaiassistent.player.FieldDef;
import com.github.fmaiassistent.player.PlayerTraits;
import com.github.fmaiassistent.linux.GameDateFinder;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fmaiassistent.service.LoadProgress;
import com.github.fmaiassistent.service.LoadProgressReporter;

import static com.github.fmaiassistent.player.AttributeDefinitions.CURRENT_ABILITY_REL;
import static com.github.fmaiassistent.player.AttributeDefinitions.CURRENT_REPUTATION_REL;
import static com.github.fmaiassistent.player.AttributeDefinitions.DISPLAY_VALUE_REL;
import static com.github.fmaiassistent.player.AttributeDefinitions.HIDDEN_DIRECT_FIELDS;
import static com.github.fmaiassistent.player.AttributeDefinitions.HISTORY_COPY_SOURCE_REL;
import static com.github.fmaiassistent.player.AttributeDefinitions.MIN_PLAYER_POSITION_SCORE;
import static com.github.fmaiassistent.player.AttributeDefinitions.HOME_REPUTATION_REL;
import static com.github.fmaiassistent.player.AttributeDefinitions.POSITION_FIELDS;
import static com.github.fmaiassistent.player.AttributeDefinitions.POTENTIAL_ABILITY_REL;
import static com.github.fmaiassistent.player.AttributeDefinitions.SOURCE_OBJECT_BASE_OFFSET;
import static com.github.fmaiassistent.player.AttributeDefinitions.VISIBLE_FIELDS;
import static com.github.fmaiassistent.player.AttributeDefinitions.WORLD_REPUTATION_REL;

public class PlayerExporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerExporter.class);
    private static final int HEIGHT_CM_REL = -0x5A;
    private static final int JOINED_CLUB_DATE_REL = -0x38;
    private static final int INJURY_REFERENCE_REL = -0x190;
    private static final int INJURY_REFERENCE_FLAG_REL = -0x18C;
    private static final int TRANSFER_STATUS_REL = 0x57;
    private static final int TRANSFER_AGREED_MARKER_REL = 0x51;
    private static final int FUTURE_TRANSFER_TABLE_REL = 0xD8;
    private static final int FUTURE_TRANSFER_CLUB_REL = 0x30;
    private static final int FUTURE_TRANSFER_DATE_REL = 0x10C;
    private static final int FUTURE_TRANSFER_CONTRACT_END_DATE_REL = 0x110;
    private static final int FUTURE_TRANSFER_ACTIVE_REL = 0x100;
    private static final int FUTURE_TRANSFER_SENTINEL_REL = 0x104;
    private static final int DUAL_PLAYER_STAFF_SHIFT = 0xF8;
    static final int PLAYER_INSERT_CHUNK = 500;
    private static final int DECODE_QUEUE_CAPACITY = 750;
    private static final int MAX_SOURCE_OFFSET = Math.max(
            POSITION_FIELDS.stream().mapToInt(FieldDef::offset).max().orElseThrow(),
            VISIBLE_FIELDS.stream().mapToInt(FieldDef::offset).max().orElseThrow())
            - SOURCE_OBJECT_BASE_OFFSET;

    public static final List<String> FIELD_NAMES = buildFieldNames();

    private final GameDateFinder gameDateFinder = new GameDateFinder();
    private final SeasonStatsReader seasonStatsReader;
    private final PlayerFieldReader playerFieldReader;

    public PlayerExporter() {
        this(new BuildSeasonStatsReader(), new BuildPlayerFieldReader());
    }

    PlayerExporter(SeasonStatsReader seasonStatsReader) {
        this(seasonStatsReader, new BuildPlayerFieldReader());
    }

    PlayerExporter(SeasonStatsReader seasonStatsReader, PlayerFieldReader playerFieldReader) {
        this.seasonStatsReader = seasonStatsReader == null ? SeasonStatsReader.unsupported() : seasonStatsReader;
        this.playerFieldReader = playerFieldReader == null ? PlayerFieldReader.unsupported() : playerFieldReader;
    }

    public ExportResult exportAllPlayers(int pid, int build, Long gamePluginBase) throws IOException {
        return exportAllPlayers(pid, build, gamePluginBase, LoadProgressReporter.NONE);
    }

    public ExportResult exportAllPlayers(
            int pid, int build, Long gamePluginBase, Consumer<LoadProgress> progress) throws IOException {
        try (ProcessMemoryReader reader = ProcessReaders.open(pid)) {
            return exportAllPlayers(reader, build, gamePluginBase, progress, null);
        }
    }

    public ExportResult exportAllPlayers(
            ProcessMemoryReader reader,
            int build,
            Long gamePluginBase,
            Consumer<LoadProgress> progress,
            Consumer<List<Map<String, Object>>> chunkSink) throws IOException {
        FmOffsets.Bounds bounds = FmOffsets.peopleBounds(reader, build, gamePluginBase);
        GamePluginIdentity pluginIdentity = GamePluginIdentity.detect(reader);
        long total = bounds.count();
        int threads = Math.min(Runtime.getRuntime().availableProcessors(), 4);
        LocalDate gameDate = gameDateFinder.find(reader, build, gamePluginBase).orElse(null);
        BlockingQueue<Map<String, Object>> decoded = new ArrayBlockingQueue<>(DECODE_QUEUE_CAPACITY);
        Set<Long> peoplePointers = ConcurrentHashMap.newKeySet();
        Map<Long, String> clubNames = new ConcurrentHashMap<>();
        SkipCounts skips = new SkipCounts();
        AtomicLong scanned = new AtomicLong();
        AtomicLong kept = new AtomicLong();
        AtomicLong seasonStatsAvailable = new AtomicLong();
        AtomicLong seasonStatsPartial = new AtomicLong();
        LoadProgressReporter reporter = new LoadProgressReporter(progress);
        reporter.start(LoadProgress.Phase.PEOPLE, total);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        List<Map<String, Object>> collected = chunkSink == null ? new ArrayList<>() : null;
        AtomicLong persistCallbackNs = new AtomicLong();
        Consumer<List<Map<String, Object>>> timedSink = chunkSink == null ? null : chunk -> {
            long started = System.nanoTime();
            chunkSink.accept(chunk);
            persistCallbackNs.addAndGet(System.nanoTime() - started);
        };
        long peopleStarted = System.nanoTime();
        try {
            long chunk = (total + threads - 1) / threads;
            for (int worker = 0; worker < threads; worker++) {
                long from = worker * chunk;
                long to = Math.min(total, from + chunk);
                futures.add(pool.submit(() -> {
                    try {
                        exportRange(
                        reader, build, gameDate, pluginIdentity, bounds.start(), from, to, clubNames, decoded, peoplePointers,
                                skips, scanned, kept, seasonStatsAvailable, seasonStatsPartial, total, reporter);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }));
            }
            List<Map<String, Object>> batch = new ArrayList<>(PLAYER_INSERT_CHUNK);
            while (true) {
                boolean finished = futures.stream().allMatch(Future::isDone);
                Map<String, Object> row = decoded.poll(50, TimeUnit.MILLISECONDS);
                if (row != null) {
                    applyGameDate(row, gameDate);
                    batch.add(row);
                    if (batch.size() >= PLAYER_INSERT_CHUNK) {
                        flushPlayerChunk(batch, collected, timedSink);
                    }
                }
                if (finished && decoded.isEmpty()) {
                    break;
                }
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    pool.shutdownNow();
                    throw new IOException("Interrupted while exporting players", e);
                } catch (ExecutionException e) {
                    pool.shutdownNow();
                    for (Future<?> pending : futures) {
                        pending.cancel(true);
                    }
                    Throwable root = e.getCause() == null ? e : e.getCause();
                    if (root instanceof OutOfMemoryError) {
                        throw new IOException(
                                "Not enough memory to export " + total
                                        + " people from RAM. Restart with start.bat (2 GB heap).",
                                root);
                    }
                    throw new IOException("Player export failed: " + root.getMessage(), root);
                }
            }
            Map<String, Object> leftover;
            while ((leftover = decoded.poll()) != null) {
                applyGameDate(leftover, gameDate);
                batch.add(leftover);
                if (batch.size() >= PLAYER_INSERT_CHUNK) {
                    flushPlayerChunk(batch, collected, timedSink);
                }
            }
            if (!batch.isEmpty()) {
                flushPlayerChunk(batch, collected, timedSink);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
            throw new IOException("Interrupted while exporting players", e);
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                pool.shutdownNow();
            }
        }
        SkipSnapshot skipSnapshot = skips.snapshot();
        reporter.finish(new LoadProgress(
                LoadProgress.Phase.PEOPLE, total, total, kept.get(), skipSnapshot.toastFragment()));
        if (skipSnapshot.decodeError() > 0) {
            LOGGER.warn("Skipped {} people records that could not be decoded", skipSnapshot.decodeError());
        }
        LOGGER.info("People table classification: {}", skipSnapshot.summary());
        String seasonStatsState = seasonStatsAvailable.get() == 0
                ? "unavailable"
                : (seasonStatsPartial.get() > 0 || seasonStatsAvailable.get() < kept.get())
                ? "partial" : "available";
        long peopleMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - peopleStarted);
        long persistMs = TimeUnit.NANOSECONDS.toMillis(persistCallbackNs.get());
        LOGGER.info("people drain {} ms (persist chunks {} ms, decode+queue {} ms)",
                peopleMs, persistMs, Math.max(0, peopleMs - persistMs));
        logHeap("after people decode");
        long tacticStarted = System.nanoTime();
        TacticExporter.Snapshot tactic = TacticExporter.export(reader, build, gamePluginBase, peoplePointers).orElse(null);
        LOGGER.info("tactic {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tacticStarted));
        logHeap("after tactic");
        if (collected != null) {
            collected.sort(Comparator.comparing(row -> String.valueOf(row.get("name")).toLowerCase()));
        }
        return new ExportResult(
                gameDate == null ? "" : gameDate.toString(),
                build,
                pluginIdentity,
                collected == null ? List.of() : collected,
                tactic,
                skipSnapshot,
                kept.get(),
                seasonStatsState,
                seasonStatsAvailable.get(),
                seasonStatsPartial.get());
    }

    private static void flushPlayerChunk(
            List<Map<String, Object>> batch,
            List<Map<String, Object>> collected,
            Consumer<List<Map<String, Object>>> chunkSink) {
        if (chunkSink != null) {
            chunkSink.accept(List.copyOf(batch));
        } else if (collected != null) {
            collected.addAll(batch);
        }
        batch.clear();
    }

    private static void logHeap(String label) {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        LOGGER.info(
                "{} heap used={} MB committed={} MB max={} MB",
                label,
                heap.getUsed() / (1024 * 1024),
                heap.getCommitted() / (1024 * 1024),
                heap.getMax() / (1024 * 1024));
    }

    void exportRange(
            ProcessMemoryReader reader,
            long slotBase,
            long from,
            long to,
            Map<Long, String> clubNames,
            List<Map<String, Object>> rows,
            SkipCounts skips) {
        BlockingQueue<Map<String, Object>> decoded = new ArrayBlockingQueue<>((int) Math.max(16, to - from + 8));
        try {
            exportRange(
                    reader, FmOffsets.DEFAULT_BUILD, null, GamePluginIdentity.unknown(), slotBase, from, to, clubNames, decoded,
                    ConcurrentHashMap.newKeySet(), skips, new AtomicLong(), new AtomicLong(),
                    new AtomicLong(), new AtomicLong(), to, new LoadProgressReporter(LoadProgressReporter.NONE));
            decoded.drainTo(rows);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void exportRange(
            ProcessMemoryReader reader,
            long slotBase,
            long from,
            long to,
            Map<Long, String> clubNames,
            List<Map<String, Object>> rows,
            AtomicInteger skipped) {
        SkipCounts skips = new SkipCounts();
        exportRange(reader, slotBase, from, to, clubNames, rows, skips);
        skipped.addAndGet(skips.decodeError.get() + skips.staff.get() + skips.noName.get() + skips.badCa.get());
    }

    void exportSlots(
            ProcessMemoryReader reader,
            long slotBase,
            long total,
            List<Map<String, Object>> rows,
            Consumer<LoadProgress> progress) {
        SkipCounts skips = new SkipCounts();
        AtomicLong scanned = new AtomicLong();
        AtomicLong kept = new AtomicLong();
        LoadProgressReporter reporter = new LoadProgressReporter(progress);
        reporter.start(LoadProgress.Phase.PEOPLE, total);
        BlockingQueue<Map<String, Object>> decoded = new ArrayBlockingQueue<>((int) Math.max(16, total + 8));
        try {
            exportRange(
                    reader, FmOffsets.DEFAULT_BUILD, null, GamePluginIdentity.unknown(), slotBase, 0, total, new ConcurrentHashMap<>(), decoded,
                    ConcurrentHashMap.newKeySet(), skips, scanned, kept, new AtomicLong(), new AtomicLong(), total, reporter);
            decoded.drainTo(rows);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        reporter.finish(new LoadProgress(LoadProgress.Phase.PEOPLE, total, total, kept.get()));
    }

    void exportRange(
            ProcessMemoryReader reader,
            long slotBase,
            long from,
            long to,
            Map<Long, String> clubNames,
            BlockingQueue<Map<String, Object>> decoded,
            Set<Long> peoplePointers,
            SkipCounts skips,
            AtomicLong scanned,
            AtomicLong kept,
            long total,
            LoadProgressReporter reporter) throws InterruptedException {
        exportRange(reader, FmOffsets.DEFAULT_BUILD, null, GamePluginIdentity.unknown(), slotBase, from, to, clubNames, decoded,
                peoplePointers, skips, scanned, kept, new AtomicLong(), new AtomicLong(), total, reporter);
    }

    private void exportRange(
            ProcessMemoryReader reader,
            int build,
            LocalDate gameDate,
            GamePluginIdentity pluginIdentity,
            long slotBase,
            long from,
            long to,
            Map<Long, String> clubNames,
            BlockingQueue<Map<String, Object>> decoded,
            Set<Long> peoplePointers,
            SkipCounts skips,
            AtomicLong scanned,
            AtomicLong kept,
            AtomicLong seasonStatsAvailable,
            AtomicLong seasonStatsPartial,
            long total,
            LoadProgressReporter reporter) throws InterruptedException {
        for (long index = from; index < to; index++) {
            long done = scanned.incrementAndGet();
            reporter.report(new LoadProgress(LoadProgress.Phase.PEOPLE, done, total, kept.get()));
            long slotAddress = slotBase + index * 8;
            var recordOpt = reader.qwordOrNull(slotAddress);
            if (recordOpt.isEmpty()) {
                skips.empty.incrementAndGet();
                continue;
            }
            long record = recordOpt.get();
            peoplePointers.add(record);
            try {
                SlotClass classified = classifyPerson(reader, record);
                if (classified.skip() != null) {
                    skips.record(classified.skip());
                    continue;
                }
                var contractedClubAddress = currentClubAddress(reader, record);
                var playingClubAddress = playingClubAddress(reader, record);
                if (playingClubAddress.isEmpty()) {
                    playingClubAddress = contractedClubAddress;
                }
                String contractedClub = clubDisplayName(reader, clubNames, contractedClubAddress);
                String playingClub = playingClubAddress.isEmpty() ? contractedClub
                        : clubDisplayName(reader, clubNames, playingClubAddress);
                if (contractedClub.isBlank() && !playingClub.isBlank() && playingClubAddress.isPresent()) {
                    contractedClubAddress = playingClubAddress;
                    contractedClub = playingClub;
                }
                Map<String, Object> row = decodeRow(
                        reader, (int) index, slotAddress, record, contractedClub, playingClub, gameDate,
                        classified.layout(), classified.name());
                applyCandidateFields(row, reader, build, record, pluginIdentity);
                applySeasonStats(row, reader, build, record, gameDate, pluginIdentity,
                        seasonStatsAvailable, seasonStatsPartial);
                contractedClubAddress.ifPresent(value -> row.put("_club_address", value));
                playingClubAddress.ifPresent(value -> row.put("_playing_club_address", value));
                kept.incrementAndGet();
                decoded.put(row);
            } catch (IOException | RuntimeException ex) {
                skips.decodeError.incrementAndGet();
                LOGGER.debug("Skipping person at slot {}: {}", index, ex.toString());
            }
        }
    }

    private void applyCandidateFields(
            Map<String, Object> row,
            ProcessMemoryReader reader,
            int build,
            long record,
            GamePluginIdentity pluginIdentity) {
        try {
            CandidatePlayerFields fields = playerFieldReader.read(reader, build, record, pluginIdentity);
            row.put("source_uid", fields.sourceUid());
            row.put("morale", fields.morale());
            row.put("condition", fields.condition());
            row.put("guide_value", fields.guideValue());
            row.put("transfer_value", fields.transferValue());
            row.put("_candidate_fields_state", fields.state().name().toLowerCase());
        } catch (IOException | RuntimeException ex) {
            row.put("source_uid", null);
            row.put("morale", null);
            row.put("condition", null);
            row.put("guide_value", null);
            row.put("transfer_value", null);
            row.put("_candidate_fields_state", "unavailable");
        }
    }

    private void applySeasonStats(
            Map<String, Object> row,
            ProcessMemoryReader reader,
            int build,
            long record,
            LocalDate gameDate,
            GamePluginIdentity pluginIdentity,
            AtomicLong available,
            AtomicLong partial) {
        try {
            SeasonStatsReader.Result result = seasonStatsReader.read(reader, build, record, gameDate, pluginIdentity);
            SeasonStats stats = result.stats();
            row.put("appearances", stats.appearances());
            row.put("starts", stats.starts());
            row.put("minutes", stats.minutes());
            row.put("goals", stats.goals());
            row.put("assists", stats.assists());
            row.put("average_rating", stats.averageRating());
            row.put("_season_stats_state", result.state().name().toLowerCase());
            if (result.state() == SeasonStatsReader.Result.State.AVAILABLE) {
                available.incrementAndGet();
            } else if (result.state() == SeasonStatsReader.Result.State.PARTIAL) {
                partial.incrementAndGet();
            }
        } catch (IOException | RuntimeException ex) {
            row.put("appearances", null);
            row.put("starts", null);
            row.put("minutes", null);
            row.put("goals", null);
            row.put("assists", null);
            row.put("average_rating", null);
            row.put("_season_stats_state", "unavailable");
        }
    }

    private static String clubDisplayName(
            ProcessMemoryReader reader,
            Map<Long, String> cache,
            java.util.Optional<Long> address) {
        if (address.isEmpty()) {
            return "";
        }
        return cache.computeIfAbsent(address.get(), value -> FmMemoryStrings.clubDisplayName(reader, value).orElse(""));
    }

    public Map<String, Object> decodeRow(ProcessMemoryReader reader, int index, long record, String club, LocalDate gameDate) throws IOException {
        String playingClub = FmMemoryStrings.playingClubName(reader, record).orElse(club);
        return decodeRow(reader, index, record, club, playingClub, gameDate);
    }

    public Map<String, Object> decodeRow(
            ProcessMemoryReader reader,
            int index,
            long record,
            String club,
            String playingClub,
            LocalDate gameDate) throws IOException {
        PlayerMemoryLayout layout = resolvePlayerLayout(reader, record).orElseGet(() -> fallbackLayout(reader, record));
        String name = FmMemoryStrings.playerName(reader, record).orElse("0x" + Long.toHexString(record));
        return decodeRow(reader, index, record, club, playingClub, gameDate, layout, name);
    }

    Map<String, Object> decodeRow(
            ProcessMemoryReader reader,
            int index,
            long record,
            String club,
            String playingClub,
            LocalDate gameDate,
            PlayerMemoryLayout layout,
            String name) throws IOException {
        return decodeRow(reader, index, 0L, record, club, playingClub, gameDate, layout, name);
    }

    Map<String, Object> decodeRow(
            ProcessMemoryReader reader,
            int index,
            long slotAddress,
            long record,
            String club,
            String playingClub,
            LocalDate gameDate,
            PlayerMemoryLayout layout,
            String name) throws IOException {
        byte[] data = layout.sourceData();
        if (data == null) {
            data = reader.readBytes(record + layout.historyCopySourceRel(), MAX_SOURCE_OFFSET + 1);
        }
        int nearbyFrom = layout.relative(HEIGHT_CM_REL);
        int nearbySize = layout.relative(WORLD_REPUTATION_REL) + 2 - nearbyFrom;
        byte[] nearby = readBytesOrEmpty(reader, record + nearbyFrom, nearbySize);
        byte[] hidden = readBytesOrEmpty(
                reader, record + HIDDEN_DIRECT_FIELDS.getFirst().offset(), HIDDEN_DIRECT_FIELDS.size());

        String loanClub = !club.isBlank() && !playingClub.equalsIgnoreCase(club) ? playingClub : "";
        LocalDate dob = dateOfBirth(reader, record);
        long displayValue = club.isBlank() ? 0L : reader.readU32(record + DISPLAY_VALUE_REL);
        Salary salary = salaryValues(reader, record);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("index", index);
        row.put("record", "0x" + Long.toHexString(record));
        row.put("name", name);
        row.put("gender", playerGender(reader, record));
        row.put("nationality", FmMemoryStrings.playerNationality(reader, record).orElse(""));
        row.put("club", club);
        row.put("playing_club", playingClub);
        row.put("loan_club", loanClub);
        row.put("is_loaned_out", loanClub.isBlank() ? "no" : "yes");
        row.put("current_reputation", nearbyU16(
                reader, nearby, nearbyFrom, record + layout.relative(CURRENT_REPUTATION_REL),
                layout.relative(CURRENT_REPUTATION_REL)));
        row.put("home_reputation", nearbyU16(
                reader, nearby, nearbyFrom, record + layout.relative(HOME_REPUTATION_REL),
                layout.relative(HOME_REPUTATION_REL)));
        row.put("world_reputation", nearbyU16(
                reader, nearby, nearbyFrom, record + layout.relative(WORLD_REPUTATION_REL),
                layout.relative(WORLD_REPUTATION_REL)));
        row.put("ca", layout.ca());
        row.put("pa", storedPa(layout.pa()));
        row.put("asking_price", AttributeDefinitions.roundObservedAskingPrice(displayValue));
        row.put("asking_price_raw", displayValue);
        row.put("joined_club_date", joinedClubDate(reader, record, club));
        PlayerStatus status = playerStatus(reader, record, layout, club, playingClub, gameDate);
        row.put("transfer_listed", status.transferListed());
        row.put("listed_for_loan", status.listedForLoan());
        row.put("transfer_agreed", status.transferAgreed());
        row.put("future_transfer_club", status.futureTransferClub());
        row.put("future_transfer_date", status.futureTransferDate());
        row.put("future_transfer_contract_end_date", status.futureTransferContractEndDate());
        row.put("injured", status.injured());
        row.put("injury", status.injury().description());
        row.put("injury_start_date", status.injury().startDate());
        row.put("injury_light_training_days_remaining", "");
        row.put("injury_full_training_days_remaining", "");
        row.put("injury_min_days_remaining", "");
        row.put("injury_max_days_remaining", "");
        row.put("injury_expected_return", "");
        row.put("_injury_status", status.injury());
        row.put("contract_end_date", contractEndDate(reader, record));
        row.put("salary_pa", salary.annualRounded());
        row.put("salary_weekly_raw", salary.weeklyRaw());
        row.put("date_of_birth", dob == null ? "" : dob.toString());
        // Always store a computed age so direct UIs and effectiveAge agree. age_as_of records only the real
        // in-game date: when the RAM date is unknown it stays empty so expiry/tenure logic is not fooled by a
        // fabricated season-baseline date, while ages remain stable against the baseline via effectiveAge().
        row.put("age", dob == null ? "" : ageOn(dob, gameDate != null ? gameDate : GameDateFinder.DEFAULT_GAME_DATE));
        row.put("age_numeric", dob == null ? null : GameDateFinder.effectiveAge(
                row.get("age") == null ? null : String.valueOf(row.get("age")),
                dob.toString(), gameDate == null ? null : gameDate.toString()));
        row.put("age_as_of", gameDate == null ? "" : gameDate.toString());
        row.put("height_cm", nearbyU8(
                reader, nearby, nearbyFrom, record + layout.relative(HEIGHT_CM_REL),
                layout.relative(HEIGHT_CM_REL)));
        row.put("traits", PlayerTraits.read(reader, record));
        row.put("source_uid", null);
        row.put("morale", null);
        row.put("condition", null);
        row.put("guide_value", null);
        row.put("transfer_value", null);
        row.put("form", null);
        row.put("appearances", null);
        row.put("starts", null);
        row.put("minutes", null);
        row.put("goals", null);
        row.put("assists", null);
        row.put("average_rating", null);

        for (FieldDef field : POSITION_FIELDS) {
            int raw = data[field.offset() - SOURCE_OBJECT_BASE_OFFSET] & 0xff;
            row.put(field.name(), AttributeDefinitions.direct20(raw));
        }
        for (FieldDef field : VISIBLE_FIELDS) {
            int raw = data[field.offset() - SOURCE_OBJECT_BASE_OFFSET] & 0xff;
            row.put(field.name(), AttributeDefinitions.norm20(raw));
        }
        for (int i = 0; i < HIDDEN_DIRECT_FIELDS.size(); i++) {
            FieldDef field = HIDDEN_DIRECT_FIELDS.get(i);
            if (hidden.length == HIDDEN_DIRECT_FIELDS.size()) {
                row.put(field.name(), hidden[i] & 0xff);
            } else {
                row.put(field.name(), reader.readU8(record + field.offset()));
            }
        }
        if (slotAddress != 0L) {
            var currentRecord = reader.qwordOrNull(slotAddress);
            if (currentRecord.isEmpty() || currentRecord.get() != record) {
                throw new IOException("record pointer moved mid-read");
            }
        }
        return row;
    }

    private static SlotClass classifyPerson(ProcessMemoryReader reader, long record) throws IOException {
        Optional<PlayerMemoryLayout> layout = resolvePlayerLayout(reader, record);
        if (layout.isEmpty()) {
            int ca = reader.readI16(record + CURRENT_ABILITY_REL);
            int alternateCa = reader.readI16(record + CURRENT_ABILITY_REL - DUAL_PLAYER_STAFF_SHIFT);
            if (validAbility(ca) || validAbility(alternateCa)) {
                return SlotClass.skip(SkipReason.STAFF);
            }
            return SlotClass.skip(SkipReason.BAD_CA);
        }
        Optional<String> name = FmMemoryStrings.playerName(reader, record);
        if (name.isEmpty()) {
            return SlotClass.skip(SkipReason.NO_NAME);
        }
        return SlotClass.keep(layout.get(), name.get());
    }

    private static Optional<PlayerMemoryLayout> resolvePlayerLayout(ProcessMemoryReader reader, long record)
            throws IOException {
        Optional<PlayerMemoryLayout> primary = tryPlayerLayout(reader, record, 0);
        if (primary.isPresent()) {
            return primary;
        }
        return tryPlayerLayout(reader, record, -DUAL_PLAYER_STAFF_SHIFT);
    }

    private static Optional<PlayerMemoryLayout> tryPlayerLayout(
            ProcessMemoryReader reader, long record, int recordRelShift) throws IOException {
        int ca = reader.readI16(record + CURRENT_ABILITY_REL + recordRelShift);
        if (!validAbility(ca)) {
            return Optional.empty();
        }
        int pa = reader.readI16(record + POTENTIAL_ABILITY_REL + recordRelShift);
        int sourceRel = HISTORY_COPY_SOURCE_REL + recordRelShift;
        byte[] data = reader.readBytes(record + sourceRel, MAX_SOURCE_OFFSET + 1);
        if (!plausiblePlayerBlock(data) || !hasPlayableRawPositions(data)) {
            return Optional.empty();
        }
        return Optional.of(new PlayerMemoryLayout(recordRelShift, sourceRel, ca, pa, data));
    }

    private static PlayerMemoryLayout fallbackLayout(ProcessMemoryReader reader, long record) {
        try {
            int ca = reader.readI16(record + CURRENT_ABILITY_REL);
            int pa = reader.readI16(record + POTENTIAL_ABILITY_REL);
            return new PlayerMemoryLayout(0, HISTORY_COPY_SOURCE_REL, ca, pa, null);
        } catch (IOException ex) {
            return new PlayerMemoryLayout(0, HISTORY_COPY_SOURCE_REL, 0, 0, null);
        }
    }

    private static Integer storedPa(int pa) {
        return validAbility(pa) ? pa : null;
    }

    private static boolean validAbility(int value) {
        return value > 0 && value <= 200;
    }

    private static boolean hasPlayableRawPositions(byte[] data) {
        int best = 0;
        for (FieldDef field : POSITION_FIELDS) {
            best = Math.max(best, data[field.offset() - SOURCE_OBJECT_BASE_OFFSET] & 0xff);
        }
        return best >= MIN_PLAYER_POSITION_SCORE;
    }

    private static boolean plausiblePlayerBlock(byte[] data) {
        long plausiblePositions = POSITION_FIELDS.stream()
                .mapToInt(field -> data[field.offset() - SOURCE_OBJECT_BASE_OFFSET] & 0xff)
                .filter(value -> value <= 20)
                .count();
        long plausibleAttributes = VISIBLE_FIELDS.stream()
                .mapToInt(field -> data[field.offset() - SOURCE_OBJECT_BASE_OFFSET] & 0xff)
                .filter(value -> value >= 1 && value <= 100)
                .count();
        return plausiblePositions >= 12 && plausibleAttributes >= 25;
    }

    private static LocalDate dateOfBirth(ProcessMemoryReader reader, long record) throws IOException {
        DatePair pair = readDatePair(reader, record + 0x88);
        return GameDateFinder.validDayYear(pair.day(), pair.year()) ? GameDateFinder.dayYearToDate(pair.day(), pair.year()) : null;
    }

    private static String joinedClubDate(ProcessMemoryReader reader, long record, String club) throws IOException {
        if (club == null || club.isBlank()) {
            return "";
        }
        var registration = reader.qwordOrNull(record + 0xA8);
        if (registration.isPresent()) {
            String registrationDate = validDate(reader, registration.get() + 0x4C);
            if (!registrationDate.isBlank()) {
                return registrationDate;
            }
        }
        return validDate(reader, record + JOINED_CLUB_DATE_REL);
    }

    private static String validDate(ProcessMemoryReader reader, long address) throws IOException {
        DatePair pair = readDatePair(reader, address);
        return GameDateFinder.validDayYear(pair.day(), pair.year())
                ? GameDateFinder.dayYearToDate(pair.day(), pair.year()).toString()
                : "";
    }

    private static String contractEndDate(ProcessMemoryReader reader, long record) throws IOException {
        var registration = reader.qwordOrNull(record + 0xA8);
        if (registration.isEmpty()) {
            return "";
        }
        DatePair pair = readDatePair(reader, registration.get() + 0x48);
        return GameDateFinder.validDayYear(pair.day(), pair.year())
                ? GameDateFinder.dayYearToDate(pair.day(), pair.year()).toString()
                : "";
    }

    private static PlayerStatus playerStatus(
            ProcessMemoryReader reader,
            long record,
            PlayerMemoryLayout layout,
            String club,
            String playingClub,
            LocalDate gameDate) throws IOException {
        var registrationOpt = reader.qwordOrNull(record + 0xA8);
        Integer transferStatus = registrationOpt
                .map(registration -> {
                    try {
                        return reader.readU8(registration + TRANSFER_STATUS_REL);
                    } catch (IOException ex) {
                        return null;
                    }
                })
                .orElse(null);
        FutureTransfer futureTransfer = registrationOpt
                .map(registration -> futureTransfer(reader, registration, club, playingClub, gameDate))
                .orElse(new FutureTransfer(null, "", "", ""));
        InjuryStatus injury = injuryStatus(reader, record);
        return new PlayerStatus(
                transferStatus == null ? null : (transferStatus & 0x01) != 0,
                transferStatus == null ? null : (transferStatus & 0x02) != 0,
                futureTransfer.transferAgreed(),
                futureTransfer.club(),
                futureTransfer.date(),
                futureTransfer.contractEndDate(),
                injury.injured(),
                injury);
    }

    private static InjuryStatus injuryStatus(ProcessMemoryReader reader, long record) throws IOException {
        final long injuryReference;
        final long injuryReferenceFlag;
        try {
            injuryReference = reader.readU64(record + INJURY_REFERENCE_REL);
            injuryReferenceFlag = reader.readU32(record + INJURY_REFERENCE_FLAG_REL);
        } catch (IOException | RuntimeException ex) {
            return new InjuryStatus(null, "", "", 0, 0);
        }
        var vectorStart = reader.qwordOrNull(injuryReference);
        boolean injured = injuryReference != 0
                && injuryReferenceFlag == 1
                && vectorStart.isPresent();
        if (!injured) {
            return new InjuryStatus(false, "", "", 0, 0);
        }
        try {
            long item = reader.qwordOrNull(vectorStart.get()).orElse(0L);
            if (item == 0) {
                return new InjuryStatus(false, "", "", 0, 0);
            }
            String description = reader.qwordOrNull(item + 0x08)
                    .flatMap(type -> FmMemoryStrings.objectStringAt(reader, type, 0x20))
                    .map(PlayerExporter::capitalizeFirst)
                    .orElse("");
            int day = GameDateFinder.maskedDay(reader.readU16(item + 0x20));
            int year = reader.readU16(item + 0x22);
            String startDate = GameDateFinder.validDayYear(day, year)
                    ? GameDateFinder.dayYearToDate(day, year).toString()
                    : "";
            int fullTrainingTotalDays = reader.readU16(item + 0x28);
            int lightTrainingTotalDays = reader.readU16(item + 0x2A);
            if (lightTrainingTotalDays > fullTrainingTotalDays) {
                lightTrainingTotalDays = fullTrainingTotalDays;
            }
            return new InjuryStatus(true, description, startDate, lightTrainingTotalDays, fullTrainingTotalDays);
        } catch (IOException | RuntimeException ex) {
            return new InjuryStatus(null, "", "", 0, 0);
        }
    }

    private static FutureTransfer futureTransfer(
            ProcessMemoryReader reader,
            long registration,
            String club,
            String playingClub,
            LocalDate gameDate) {
        try {
            if (reader.readU8(registration + TRANSFER_AGREED_MARKER_REL) == 0
                    || reader.readI32(registration + FUTURE_TRANSFER_ACTIVE_REL) != 0
                    || reader.readI32(registration + FUTURE_TRANSFER_SENTINEL_REL) != -1) {
                return new FutureTransfer(false, "", "", "");
            }
            String futureClub = reader.qwordOrNull(registration + FUTURE_TRANSFER_TABLE_REL)
                    .flatMap(table -> reader.qwordOrNull(table + FUTURE_TRANSFER_CLUB_REL))
                    .flatMap(futureClubAddress -> FmMemoryStrings.clubDisplayName(reader, futureClubAddress))
                    .orElse("");
            if (futureClub.isBlank()
                    || futureClub.equalsIgnoreCase(club == null ? "" : club)
                    || futureClub.equalsIgnoreCase(playingClub == null ? "" : playingClub)) {
                return new FutureTransfer(false, "", "", "");
            }
            DatePair pair = readDatePair(reader, registration + FUTURE_TRANSFER_DATE_REL);
            if (!GameDateFinder.validDayYear(pair.day(), pair.year())) {
                return new FutureTransfer(false, "", "", "");
            }
            LocalDate date = GameDateFinder.dayYearToDate(pair.day(), pair.year());
            Boolean agreed = gameDate == null ? null : date.isAfter(gameDate);
            String contractEndDate = "";
            DatePair contractEndPair = readDatePair(reader, registration + FUTURE_TRANSFER_CONTRACT_END_DATE_REL);
            if (GameDateFinder.validDayYear(contractEndPair.day(), contractEndPair.year())) {
                contractEndDate = GameDateFinder.dayYearToDate(contractEndPair.day(), contractEndPair.year()).toString();
            }
            return new FutureTransfer(agreed, futureClub, date.toString(), contractEndDate);
        } catch (IOException | RuntimeException ex) {
            return new FutureTransfer(null, "", "", "");
        }
    }

    private static java.util.Optional<Long> currentClubAddress(ProcessMemoryReader reader, long record) {
        return reader.qwordOrNull(record + 0xA8)
                .flatMap(registration -> reader.qwordOrNull(registration + 0x10))
                .flatMap(registrationBody -> reader.qwordOrNull(registrationBody + 0x30));
    }

    private static java.util.Optional<Long> playingClubAddress(ProcessMemoryReader reader, long record) {
        return reader.qwordOrNull(record - 0x158)
                .flatMap(teamBody -> reader.qwordOrNull(teamBody + 0x30));
    }

    private static String playerGender(ProcessMemoryReader reader, long record) throws IOException {
        int value = reader.readU8(record + 0x19);
        return (value & 0x10) != 0 ? "female" : "male";
    }

    private static Salary salaryValues(ProcessMemoryReader reader, long record) throws IOException {
        var registration = reader.qwordOrNull(record + 0xA8);
        if (registration.isEmpty()) {
            return salaryFromWeeklyRaw(null);
        }
        return salaryFromWeeklyRaw(reader.readU32(registration.get() + 0x20));
    }

    static Salary salaryFromWeeklyRaw(Long weeklyRaw) {
        if (weeklyRaw == null) {
            return new Salary(null, null);
        }
        long annualRaw = weeklyRaw * 52;
        return new Salary(weeklyRaw, Math.round(annualRaw / 1000.0) * 1000);
    }

    private static DatePair readDatePair(ProcessMemoryReader reader, long address) throws IOException {
        int day = GameDateFinder.maskedDay(reader.readU16(address));
        int year = reader.readU16(address + 2);
        return new DatePair(day, year);
    }

    private static int ageOn(LocalDate dob, LocalDate gameDate) {
        int age = gameDate.getYear() - dob.getYear();
        if (gameDate.getMonthValue() < dob.getMonthValue()
                || (gameDate.getMonthValue() == dob.getMonthValue() && gameDate.getDayOfMonth() < dob.getDayOfMonth())) {
            age--;
        }
        return Math.max(0, age);
    }

    private static void applyGameDate(Map<String, Object> row, LocalDate gameDate) {
        applyGameDate(List.of(row), gameDate);
    }

    private static void applyGameDate(List<Map<String, Object>> rows, LocalDate gameDate) {
        for (Map<String, Object> row : rows) {
            String dobValue = String.valueOf(row.getOrDefault("date_of_birth", ""));
            try {
                row.put("age", dobValue.isBlank() ? ""
                        : ageOn(LocalDate.parse(dobValue), gameDate != null ? gameDate : GameDateFinder.DEFAULT_GAME_DATE));
                row.put("age_as_of", gameDate == null ? "" : gameDate.toString());
            } catch (DateTimeException ex) {
                row.put("age", "");
                row.put("age_as_of", "");
            }
            String futureTransferDate = String.valueOf(row.getOrDefault("future_transfer_date", ""));
            if (gameDate == null) {
                applyInjuryRemaining(row, gameDate);
                continue;
            }
            if (futureTransferDate.isBlank()) {
                if (row.get("transfer_agreed") != null) {
                    row.put("transfer_agreed", false);
                }
                row.put("future_transfer_club", "");
                row.put("future_transfer_contract_end_date", "");
            } else {
                try {
                    boolean active = LocalDate.parse(futureTransferDate).isAfter(gameDate);
                    row.put("transfer_agreed", active);
                    if (!active) {
                        row.put("future_transfer_club", "");
                        row.put("future_transfer_date", "");
                        row.put("future_transfer_contract_end_date", "");
                    }
                } catch (DateTimeException ex) {
                    row.put("transfer_agreed", false);
                    row.put("future_transfer_club", "");
                    row.put("future_transfer_date", "");
                    row.put("future_transfer_contract_end_date", "");
                }
            }
            applyInjuryRemaining(row, gameDate);
        }
    }

    private static void applyInjuryRemaining(Map<String, Object> row, LocalDate gameDate) {
        if (!Boolean.TRUE.equals(row.get("injured"))) {
            row.put("injury_light_training_days_remaining", "");
            row.put("injury_full_training_days_remaining", "");
            row.put("injury_min_days_remaining", "");
            row.put("injury_max_days_remaining", "");
            row.put("injury_expected_return", "");
            row.remove("_injury_status");
            return;
        }
        String startDateValue = String.valueOf(row.getOrDefault("injury_start_date", ""));
        if (gameDate == null || startDateValue.isBlank()) {
            row.remove("_injury_status");
            return;
        }
        try {
            LocalDate startDate = LocalDate.parse(startDateValue);
            long elapsed = java.time.temporal.ChronoUnit.DAYS.between(startDate, gameDate);
            InjuryStatus injury = (InjuryStatus) row.get("_injury_status");
            int lightTrainingDays = Math.max(0, injury.lightTrainingTotalDays() - (int) elapsed);
            int fullTrainingDays = Math.max(lightTrainingDays, injury.fullTrainingTotalDays() - (int) elapsed);
            row.put("injury_light_training_days_remaining", lightTrainingDays);
            row.put("injury_full_training_days_remaining", fullTrainingDays);
            row.put("injury_min_days_remaining", lightTrainingDays);
            row.put("injury_max_days_remaining", fullTrainingDays);
            row.put("injury_expected_return", formatInjuryDays(fullTrainingDays));
        } catch (DateTimeException | ClassCastException ex) {
            row.put("injury_light_training_days_remaining", "");
            row.put("injury_full_training_days_remaining", "");
            row.put("injury_min_days_remaining", "");
            row.put("injury_max_days_remaining", "");
            row.put("injury_expected_return", "");
        } finally {
            row.remove("_injury_status");
        }
    }

    private static String formatInjuryDays(int days) {
        return days + " days";
    }

    private static String capitalizeFirst(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static List<String> buildFieldNames() {
        List<String> names = new ArrayList<>(List.of(
                "index", "record", "name", "gender", "nationality", "club", "playing_club", "loan_club", "is_loaned_out",
                "current_reputation", "home_reputation", "world_reputation", "ca", "pa",
                "asking_price", "asking_price_raw", "joined_club_date", "transfer_listed", "listed_for_loan",
                "transfer_agreed", "future_transfer_club", "future_transfer_date", "future_transfer_contract_end_date", "injured",
                "injury", "injury_start_date", "injury_light_training_days_remaining", "injury_full_training_days_remaining",
                "injury_min_days_remaining", "injury_max_days_remaining", "injury_expected_return",
                "contract_end_date", "salary_pa",
                "salary_weekly_raw", "date_of_birth", "age", "age_numeric", "age_as_of", "height_cm",
                "traits", "source_uid", "morale", "condition", "guide_value", "transfer_value", "candidate_fields_state", "form",
                "appearances", "starts", "minutes", "goals", "assists", "average_rating"));
        POSITION_FIELDS.stream().map(FieldDef::name).forEach(names::add);
        VISIBLE_FIELDS.stream().map(FieldDef::name).forEach(names::add);
        HIDDEN_DIRECT_FIELDS.stream().map(FieldDef::name).forEach(names::add);
        return List.copyOf(names);
    }

    public record ExportResult(
            String gameDate,
            int build,
            GamePluginIdentity pluginIdentity,
            List<Map<String, Object>> rows,
            TacticExporter.Snapshot tactic,
            SkipSnapshot skips,
            long kept,
            String seasonStatsState,
            long seasonStatsAvailable,
            long seasonStatsPartial) {
        public ExportResult(String gameDate, List<Map<String, Object>> rows) {
            this(gameDate, FmOffsets.DEFAULT_BUILD, GamePluginIdentity.unknown(), rows, null, SkipSnapshot.EMPTY,
                    rows == null ? 0 : rows.size(), "unavailable", 0, 0);
        }

        public ExportResult(String gameDate, List<Map<String, Object>> rows, TacticExporter.Snapshot tactic) {
            this(gameDate, FmOffsets.DEFAULT_BUILD, GamePluginIdentity.unknown(), rows, tactic, SkipSnapshot.EMPTY,
                    rows == null ? 0 : rows.size(), "unavailable", 0, 0);
        }
    }

    public record SkipSnapshot(int empty, int staff, int noName, int badCa, int decodeError) {
        public static final SkipSnapshot EMPTY = new SkipSnapshot(0, 0, 0, 0, 0);

        public int totalRejected() {
            return empty + staff + noName + badCa + decodeError;
        }

        public String summary() {
            return "empty " + empty
                    + ", staff " + staff
                    + ", no name " + noName
                    + ", bad CA " + badCa
                    + ", decode errors " + decodeError;
        }

        public String toastFragment() {
            List<String> parts = new ArrayList<>();
            if (staff > 0) {
                parts.add(staff + " staff skipped");
            }
            if (noName > 0) {
                parts.add(noName + " unnamed skipped");
            }
            if (badCa > 0) {
                parts.add(badCa + " bad CA skipped");
            }
            if (decodeError > 0) {
                parts.add(decodeError + " decode errors");
            }
            return parts.isEmpty() ? "" : " · " + String.join(", ", parts);
        }
    }

    static final class SkipCounts {
        final AtomicInteger empty = new AtomicInteger();
        final AtomicInteger staff = new AtomicInteger();
        final AtomicInteger noName = new AtomicInteger();
        final AtomicInteger badCa = new AtomicInteger();
        final AtomicInteger decodeError = new AtomicInteger();

        void record(SkipReason reason) {
            switch (reason) {
                case EMPTY -> empty.incrementAndGet();
                case STAFF -> staff.incrementAndGet();
                case NO_NAME -> noName.incrementAndGet();
                case BAD_CA -> badCa.incrementAndGet();
            }
        }

        SkipSnapshot snapshot() {
            return new SkipSnapshot(empty.get(), staff.get(), noName.get(), badCa.get(), decodeError.get());
        }
    }

    private enum SkipReason {
        EMPTY, STAFF, NO_NAME, BAD_CA
    }

    private record SlotClass(SkipReason skip, PlayerMemoryLayout layout, String name) {
        static SlotClass skip(SkipReason reason) {
            return new SlotClass(reason, null, null);
        }

        static SlotClass keep(PlayerMemoryLayout layout, String name) {
            return new SlotClass(null, layout, name);
        }
    }

    private record DatePair(int day, int year) {
    }

    static record Salary(Long weeklyRaw, Long annualRounded) {
    }

    private record PlayerStatus(
            Boolean transferListed,
            Boolean listedForLoan,
            Boolean transferAgreed,
            String futureTransferClub,
            String futureTransferDate,
            String futureTransferContractEndDate,
            Boolean injured,
            InjuryStatus injury) {
    }

    private record InjuryStatus(Boolean injured, String description, String startDate, int lightTrainingTotalDays, int fullTrainingTotalDays) {
    }

    private static int u8At(byte[] data, int baseRel, int fieldRel) {
        int index = fieldRel - baseRel;
        if (index < 0 || index >= data.length) {
            return 0;
        }
        return data[index] & 0xff;
    }

    private static int u16At(byte[] data, int baseRel, int fieldRel) {
        int index = fieldRel - baseRel;
        if (index < 0 || index + 1 >= data.length) {
            return 0;
        }
        return (data[index] & 0xff) | ((data[index + 1] & 0xff) << 8);
    }

    private static byte[] readBytesOrEmpty(ProcessMemoryReader reader, long address, int size) {
        try {
            return reader.readBytes(address, size);
        } catch (IOException | RuntimeException ex) {
            LOGGER.debug("readBytes failed at 0x{} (size {}): {}",
                    Long.toHexString(address), size, ex.toString());
            return new byte[0];
        }
    }

    private static int nearbyU8(
            ProcessMemoryReader reader, byte[] nearby, int baseRel, long address, int fieldRel) throws IOException {
        if (nearby.length > 0) {
            return u8At(nearby, baseRel, fieldRel);
        }
        return reader.readU8(address);
    }

    private static int nearbyU16(
            ProcessMemoryReader reader, byte[] nearby, int baseRel, long address, int fieldRel) throws IOException {
        if (nearby.length > 0) {
            return u16At(nearby, baseRel, fieldRel);
        }
        return reader.readU16(address);
    }

    private record FutureTransfer(Boolean transferAgreed, String club, String date, String contractEndDate) {
    }

    private record PlayerMemoryLayout(
            int recordRelShift, int historyCopySourceRel, int ca, int pa, byte[] sourceData) {
        private int relative(int rel) {
            return rel + recordRelShift;
        }
    }
}
