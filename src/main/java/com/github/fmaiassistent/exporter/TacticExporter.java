package com.github.fmaiassistent.exporter;

import com.github.fmaiassistent.linux.FmFormations;
import com.github.fmaiassistent.linux.FmOffsets;
import com.github.fmaiassistent.linux.MemoryRegion;
import com.github.fmaiassistent.memory.ProcessMemoryReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class TacticExporter {
    static final long TACTICS_MANAGER_VTABLE_RVA = 0x46A2B90L;
    static final int FORMATION_REL = 0x03D4;
    static final int SELECTION_POINTER_REL = 0x41B8;
    static final int SELECTED_PERSONS_REL = 0x1B00;
    private static final int MIN_SELECTED_PEOPLE = 8;
    private static final long MAX_SCAN_REGION_SIZE = 64L * 1024 * 1024;
    private static final long MAX_TOTAL_SCAN = 256L * 1024 * 1024;

    private TacticExporter() {
    }

    public record Snapshot(String formation, List<String> positions, List<Long> selectedPersonAddresses) {
        public String slotText() {
            return String.join("\n", positions.stream().map(position -> position + ",,").toList());
        }
    }

    public static Optional<Snapshot> export(
            ProcessMemoryReader reader,
            int build,
            Long gamePluginBase) throws IOException {
        long pluginBase = gamePluginBase == null ? FmOffsets.findGamePluginBase(reader) : gamePluginBase;
        Set<Long> people = peoplePointers(reader, build, pluginBase);
        if (people.size() < 100) {
            return Optional.empty();
        }
        long vtable = pluginBase + TACTICS_MANAGER_VTABLE_RVA;
        return scanForManager(reader, vtable, people);
    }

    private static Optional<Snapshot> scanForManager(ProcessMemoryReader reader, long vtable, Set<Long> people)
            throws IOException {
        long scanned = 0;
        Snapshot best = null;
        int bestHits = 0;
        List<MemoryRegion> regions = reader.maps().stream()
                .filter(MemoryRegion::readable)
                .filter(MemoryRegion::writable)
                .filter(region -> region.size() > 0 && region.size() <= MAX_SCAN_REGION_SIZE)
                .toList();
        for (MemoryRegion region : regions) {
            if (scanned >= MAX_TOTAL_SCAN) {
                break;
            }
            byte[] data;
            try {
                data = reader.readBytes(region.start(), Math.toIntExact(region.size()));
            } catch (IOException | ArithmeticException ex) {
                continue;
            }
            scanned += data.length;
            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            for (int offset = 0; offset <= data.length - 8; offset += 8) {
                if (buffer.getLong(offset) != vtable) {
                    continue;
                }
                Optional<Snapshot> decoded = decodeManager(reader, region.start() + offset, people);
                if (decoded.isEmpty()) {
                    continue;
                }
                int hits = (int) decoded.get().selectedPersonAddresses().stream().filter(people::contains).count();
                if (hits > bestHits) {
                    bestHits = hits;
                    best = decoded.get();
                }
            }
        }
        return Optional.ofNullable(best);
    }

    static Optional<Snapshot> decodeManager(ProcessMemoryReader reader, long manager, Set<Long> people) {
        try {
            int formation = reader.readI32(manager + FORMATION_REL);
            Optional<FmFormations.Shape> shape = FmFormations.shape(formation);
            if (shape.isEmpty()) {
                return Optional.empty();
            }
            List<Long> selected = selectedPeople(reader, manager);
            long hits = selected.stream().filter(people::contains).count();
            if (hits < MIN_SELECTED_PEOPLE) {
                return Optional.empty();
            }
            return Optional.of(new Snapshot(shape.get().name(), shape.get().positions(), selected));
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    static List<Long> selectedPeople(ProcessMemoryReader reader, long manager) throws IOException {
        Optional<Long> block = reader.qwordOrNull(manager + SELECTION_POINTER_REL);
        if (block.isEmpty()) {
            return List.of();
        }
        List<Long> selected = new ArrayList<>(11);
        for (int index = 0; index < 11; index++) {
            selected.add(reader.qwordOrNull(block.get() + SELECTED_PERSONS_REL + index * 8L).orElse(0L));
        }
        return selected;
    }

    static Set<Long> peoplePointers(ProcessMemoryReader reader, int build, long gamePluginBase) throws IOException {
        FmOffsets.Bounds bounds = FmOffsets.peopleBounds(reader, build, gamePluginBase);
        Set<Long> people = new HashSet<>();
        for (long index = 0; index < bounds.count(); index++) {
            reader.qwordOrNull(bounds.start() + index * 8).ifPresent(people::add);
        }
        return people;
    }
}
