package com.github.fmaiassistent.linux;

import com.github.fmaiassistent.memory.ProcessMemoryReader;
import com.github.fmaiassistent.memory.ProcessReaders;

import java.util.Comparator;
import java.util.List;

public final class RamAttachProbe {
    private RamAttachProbe() {
    }

    public static void main(String[] args) throws Exception {
        List<ProcessInfo> processes = ProcessReaders.findProcesses("fm.exe");
        System.out.println("fm.exe matches: " + processes.size());
        for (ProcessInfo process : processes) {
            System.out.println("  pid=" + process.pid() + " name=" + process.name());
        }
        int pid = processes.stream()
                .max(Comparator.comparingInt(process -> "fm.exe".equalsIgnoreCase(process.name()) ? 1 : 0))
                .map(ProcessInfo::pid)
                .orElseThrow(() -> new IllegalStateException("fm.exe not running"));
        try (ProcessMemoryReader reader = ProcessReaders.open(pid)) {
            System.out.println("OpenProcess ok pid=" + pid);
            List<MemoryRegion> maps = reader.maps();
            System.out.println("committed regions: " + maps.size());
            maps.stream()
                    .map(MemoryRegion::path)
                    .filter(path -> path.toLowerCase().contains("plugin") || path.toLowerCase().endsWith("fm.exe"))
                    .distinct()
                    .forEach(path -> System.out.println("  module " + path));
            long base = FmOffsets.findGamePluginBase(reader);
            System.out.println("game_plugin base=0x" + Long.toHexString(base));
            FmOffsets.Bounds people = FmOffsets.peopleBounds(reader, FmOffsets.DEFAULT_BUILD, base);
            System.out.println("people count=" + people.count());
            System.out.println("slot counts=" + FmOffsets.slotCounts(reader, FmOffsets.DEFAULT_BUILD, base));
        }
        var competitions = new com.github.fmaiassistent.exporter.CompetitionExporter()
                .exportAllCompetitions(pid, FmOffsets.DEFAULT_BUILD, null);
        System.out.println("decoded competitions=" + competitions.rows().size());
        var clubs = new com.github.fmaiassistent.exporter.ClubExporter()
                .exportAllClubs(pid, FmOffsets.DEFAULT_BUILD, null);
        System.out.println("decoded clubs=" + clubs.rows().size());
        var players = new com.github.fmaiassistent.exporter.PlayerExporter()
                .exportAllPlayers(pid, FmOffsets.DEFAULT_BUILD, null);
        System.out.println("decoded players=" + players.rows().size() + " gameDate=" + players.gameDate());
    }
}
