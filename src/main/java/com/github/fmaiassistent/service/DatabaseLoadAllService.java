package com.github.fmaiassistent.service;

import com.github.fmaiassistent.exporter.ClubExporter;
import com.github.fmaiassistent.exporter.CompetitionExporter;
import com.github.fmaiassistent.exporter.PlayerExporter;
import com.github.fmaiassistent.linux.FmOffsets;
import com.github.fmaiassistent.linux.ProcessInfo;
import com.github.fmaiassistent.memory.ProcessMemoryReader;
import com.github.fmaiassistent.memory.ProcessReaders;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DatabaseLoadAllService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseLoadAllService.class);
    private final SnapshotPersistService persist;
    private final CompetitionExporter competitionExporter = new CompetitionExporter();
    private final ClubExporter clubExporter = new ClubExporter();
    private final PlayerExporter playerExporter = new PlayerExporter();

    public DatabaseLoadAllService(SnapshotPersistService persist) {
        this.persist = persist;
    }

    public LoadAllResult loadAll(Integer pid, int build, Long gamePluginBase) throws IOException {
        int resolvedPid = pid == null ? detectFmPid() : pid;
        LOGGER.info("Loading RAM snapshot from fm.exe pid {}", resolvedPid);
        try {
            CompetitionExporter.ExportResult competitionRows =
                    competitionExporter.exportAllCompetitions(resolvedPid, build, gamePluginBase);
            ClubExporter.ExportResult clubRows = clubExporter.exportAllClubs(resolvedPid, build, gamePluginBase);
            PlayerExporter.ExportResult playerRows = playerExporter.exportAllPlayers(resolvedPid, build, gamePluginBase);
            if (playerRows.rows().isEmpty()) {
                throw new IllegalStateException(
                        "RAM export found no players. Previous snapshot kept. Is FM26 running with a save loaded?");
            }
            LoadAllResult result = persist.persist(resolvedPid, competitionRows, clubRows, playerRows);
            LOGGER.info(
                    "Loaded RAM snapshot: {} players, {} clubs, {} competitions",
                    result.players(),
                    result.clubs(),
                    result.competitions());
            return result;
        } catch (RuntimeException | IOException ex) {
            LOGGER.error("RAM snapshot failed for pid {}", resolvedPid, ex);
            throw ex;
        }
    }

    public Map<String, Long> ramSlotCounts() throws IOException {
        int pid = detectFmPid();
        try (ProcessMemoryReader reader = ProcessReaders.open(pid)) {
            return FmOffsets.slotCounts(reader, LoadAllResult.defaultBuild(), null);
        }
    }

    public int detectFmPid() throws IOException {
        return ProcessReaders.findProcesses("fm.exe").stream()
                .max(Comparator.comparingInt(DatabaseLoadAllService::processScore))
                .filter(process -> processScore(process) > 0)
                .map(ProcessInfo::pid)
                .orElseThrow(() -> new IllegalStateException("fm.exe process not found"));
    }

    private static int processScore(ProcessInfo process) {
        String name = process.name().toLowerCase();
        String cmdline = process.cmdline().toLowerCase();
        int score = 0;
        if ("fm.exe".equals(name)) {
            score += 100;
        }
        if (cmdline.contains("football manager 26")) {
            score += 50;
        }
        if (cmdline.endsWith("fm.exe") || cmdline.endsWith("fm.exe\"")) {
            score += 25;
        }
        if (cmdline.contains("proton") || cmdline.contains("steamlaunch") || cmdline.contains("reaper")
                || cmdline.contains("bwrap")) {
            if (!"fm.exe".equals(name)) {
                score -= 100;
            }
        }
        return score;
    }

    public record LoadAllResult(int pid, String gameDate, long players, long clubs, long competitions) {
        public static int defaultBuild() {
            return FmOffsets.DEFAULT_BUILD;
        }
    }
}
