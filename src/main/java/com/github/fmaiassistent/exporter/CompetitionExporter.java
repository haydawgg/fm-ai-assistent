package com.github.fmaiassistent.exporter;

import com.github.fmaiassistent.linux.FmMemoryStrings;
import com.github.fmaiassistent.linux.FmOffsets;
import com.github.fmaiassistent.memory.ProcessMemoryReader;
import com.github.fmaiassistent.memory.ProcessReaders;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.github.fmaiassistent.service.LoadProgress;
import com.github.fmaiassistent.service.LoadProgressReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompetitionExporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompetitionExporter.class);
    public static final List<String> FIELD_NAMES = List.of("sourceAddress", "name", "nation", "reputation", "gender");

    private static final long NAME_REL = 0x40;
    private static final long NATION_REL = 0x60;
    private static final long GENDER_FLAG_REL = 0xF9;
    private static final long REPUTATION_REL = 0x188;

    public ExportResult exportAllCompetitions(int pid, int build, Long gamePluginBase) throws IOException {
        return exportAllCompetitions(pid, build, gamePluginBase, LoadProgressReporter.NONE);
    }

    public ExportResult exportAllCompetitions(
            int pid, int build, Long gamePluginBase, Consumer<LoadProgress> progress) throws IOException {
        try (ProcessMemoryReader reader = ProcessReaders.open(pid)) {
            FmOffsets.Bounds bounds = FmOffsets.tableBounds(reader, build, gamePluginBase, "CompetitionOffset");
            long total = bounds.count();
            LoadProgressReporter reporter = new LoadProgressReporter(progress);
            reporter.start(LoadProgress.Phase.COMPETITIONS, total);
            Map<Long, Map<String, Object>> byCompetition = new LinkedHashMap<>();
            int skipped = 0;
            for (long index = 0; index < total; index++) {
                long slotAddress = bounds.start() + index * 8;
                var competitionOpt = reader.qwordOrNull(slotAddress);
                if (competitionOpt.isEmpty()) {
                    reporter.report(new LoadProgress(
                            LoadProgress.Phase.COMPETITIONS, index + 1, total, byCompetition.size()));
                    continue;
                }
                long competition = competitionOpt.get();
                try {
                    Map<String, Object> row = decodeCompetition(reader, competition);
                    if (!row.isEmpty()) {
                        byCompetition.put(competition, row);
                    }
                } catch (IOException | RuntimeException ex) {
                    skipped++;
                    LOGGER.debug("Skipping competition at slot {}: {}", index, ex.toString());
                }
                reporter.report(new LoadProgress(
                        LoadProgress.Phase.COMPETITIONS, index + 1, total, byCompetition.size()));
            }
            if (skipped > 0) {
                LOGGER.warn("Skipped {} competition records that could not be decoded", skipped);
            }
            List<Map<String, Object>> rows = new ArrayList<>(byCompetition.values());
            rows.sort(Comparator.comparing(row -> String.valueOf(row.get("name")).toLowerCase()));
            reporter.finish(new LoadProgress(LoadProgress.Phase.COMPETITIONS, total, total, rows.size()));
            return new ExportResult(rows);
        }
    }

    private Map<String, Object> decodeCompetition(ProcessMemoryReader reader, long competition) throws IOException {
        String name = FmMemoryStrings.objectStringAt(reader, competition, NAME_REL)
                .or(() -> FmMemoryStrings.competitionDisplayName(reader, competition))
                .orElse("");
        if (name.isBlank()) {
            return Map.of();
        }
        int reputation = reader.readU16(competition + REPUTATION_REL);
        if (reputation <= 0 || reputation > 10_000) {
            return Map.of();
        }
        String nation = reader.qwordOrNull(competition + NATION_REL)
                .flatMap(value -> FmMemoryStrings.objectStringAt(reader, value, 0x18)
                        .or(() -> FmMemoryStrings.objectStringAt(reader, value, 0x20)))
                .orElse("");
        if (nation.isBlank()) {
            return Map.of();
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sourceAddress", competition);
        row.put("name", name);
        row.put("nation", nation);
        row.put("reputation", reputation);
        row.put("gender", reader.readU8(competition + GENDER_FLAG_REL) == 1 ? "female" : "male");
        return row;
    }

    public record ExportResult(List<Map<String, Object>> rows) {
    }
}
