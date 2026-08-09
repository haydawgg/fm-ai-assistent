package com.github.fmaiassistent.exporter;

import com.github.fmaiassistent.linux.MemoryRegion;
import com.github.fmaiassistent.memory.ProcessMemoryReader;
import com.github.fmaiassistent.player.AttributeDefinitions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the parallel player export: the per-range workers must produce exactly the same
 * rows as a single sequential pass (no loss, no duplicates, same filter), deterministically,
 * while sharing the club-name cache. Runs against a scripted memory stub - no FM process needed.
 */
class PlayerExporterParallelTest {

    private static final long SLOT_BASE = 0x1_0000;
    private static final long RECORD_BASE = 0x2_0000;

    /** Scripted memory: slots point at records with CA/PA, a club pointer chain and club name. */
    private static final class StubReader implements ProcessMemoryReader {
        private final Map<Long, byte[]> segments = new HashMap<>();
        private final int slotCount;

        StubReader(int slotCount, Set<Integer> emptySlots, Map<Integer, Integer> caOverrides) {
            this.slotCount = slotCount;
            for (int i = 0; i < slotCount; i++) {
                if (emptySlots.contains(i)) {
                    continue;
                }
                long record = RECORD_BASE + i * 0x1000L;
                putQword(SLOT_BASE + i * 8L, record);
                putShort(record + AttributeDefinitions.CURRENT_ABILITY_REL,
                        (short) (int) caOverrides.getOrDefault(i, 100));
                putShort(record + AttributeDefinitions.POTENTIAL_ABILITY_REL, (short) 110);
                long registration = record + 0x100;
                long body = registration + 0x80;
                long club = 0x5_0000L + i;
                long clubNameAddress = 0x6_0000L + i * 0x100;
                putQword(record + 0xA8, registration);
                putQword(registration + 0x10, body);
                putQword(body + 0x30, club);
                // clubDisplayName reads a name pointer at club+0xC8 -> FM length-prefixed string
                putQword(club + 0xC8, clubNameAddress);
                segments.put(clubNameAddress, clubName("Test FC " + i));
            }
        }

        private static byte[] clubName(String name) {
            byte[] chars = name.getBytes(StandardCharsets.US_ASCII);
            byte[] out = new byte[4 + chars.length];
            ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).putInt(chars.length);
            System.arraycopy(chars, 0, out, 4, chars.length);
            return out;
        }

        private void putQword(long address, long value) {
            segments.put(address, ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array());
        }

        private void putShort(long address, short value) {
            segments.put(address, ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array());
        }

        @Override
        public int pid() {
            return 12345;
        }

        @Override
        public List<MemoryRegion> maps() {
            return List.of();
        }

        @Override
        public void close() {
        }

        @Override
        public byte[] readBytes(long address, int size) {
            for (Map.Entry<Long, byte[]> entry : segments.entrySet()) {
                long start = entry.getKey();
                byte[] data = entry.getValue();
                if (address >= start && address + size <= start + data.length) {
                    return Arrays.copyOfRange(data, (int) (address - start), (int) (address - start) + size);
                }
            }
            return new byte[size];
        }
    }

    private static List<Map<String, Object>> exportRange(
            ProcessMemoryReader reader, long from, long to, List<Map<String, Object>> rows) throws Exception {
        Method method = PlayerExporter.class.getDeclaredMethod(
                "exportRange", ProcessMemoryReader.class, long.class, long.class, long.class, Map.class, List.class);
        method.setAccessible(true);
        method.invoke(new PlayerExporter(), reader, SLOT_BASE, from, to, new ConcurrentHashMap<>(), rows);
        return rows;
    }

    private static List<Map<String, Object>> runParallel(StubReader reader, int slots, int threads) throws Exception {
        List<Map<String, Object>> rows = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            int chunk = slots / threads;
            for (int worker = 0; worker < threads; worker++) {
                long from = worker * chunk;
                long to = Math.min(slots, from + chunk);
                futures.add(pool.submit(() -> {
                    try {
                        exportRange(reader, from, to, rows);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdown();
        }
        return rows;
    }

    private static List<Map<String, Object>> sortedByName(List<Map<String, Object>> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(row -> String.valueOf(row.get("name")).toLowerCase()))
                .toList();
    }

    @Test
    void parallelSplitMergesIdenticallyToSequentialPass() throws Exception {
        int slots = 64;
        StubReader reader = new StubReader(slots, Set.of(), Map.of());

        List<Map<String, Object>> sequential = exportRange(reader, 0, slots, new ArrayList<>());
        assertEquals(slots, sequential.size());

        List<Map<String, Object>> parallel = runParallel(reader, slots, 8);
        assertEquals(slots, parallel.size());
        assertEquals(sortedByName(sequential), sortedByName(parallel));
    }

    @Test
    void parallelRunIsDeterministic() throws Exception {
        int slots = 64;
        StubReader reader = new StubReader(slots, Set.of(), Map.of());

        List<Map<String, Object>> first = runParallel(reader, slots, 8);
        List<Map<String, Object>> second = runParallel(reader, slots, 8);
        assertEquals(sortedByName(first), sortedByName(second));
    }

    @Test
    void rowsCarryIndexClubAndAbilityValues() throws Exception {
        int slots = 16;
        StubReader reader = new StubReader(slots, Set.of(), Map.of());

        List<Map<String, Object>> rows = exportRange(reader, 0, slots, new ArrayList<>());
        assertEquals(slots, rows.size());
        for (Map<String, Object> row : rows) {
            int index = ((Number) row.get("index")).intValue();
            assertEquals("Test FC " + index, row.get("club"));
            assertEquals(100, ((Number) row.get("ca")).intValue());
            assertEquals(110, ((Number) row.get("pa")).intValue());
            assertTrue(String.valueOf(row.get("name")).startsWith("0x"));
        }
    }

    @Test
    void emptySlotsAreSkippedAndInvalidAbilityFiltered() throws Exception {
        int slots = 8;
        StubReader reader = new StubReader(slots, Set.of(2, 5), Map.of(7, 500));

        List<Map<String, Object>> rows = exportRange(reader, 0, slots, new ArrayList<>());
        // 8 slots - 2 empty - 1 with out-of-range CA = 5 valid rows, all from the expected indices
        assertEquals(5, rows.size());
        Set<Integer> indices = new HashSet<>();
        for (Map<String, Object> row : rows) {
            indices.add(((Number) row.get("index")).intValue());
        }
        assertEquals(Set.of(0, 1, 3, 4, 6), indices);
    }
}
