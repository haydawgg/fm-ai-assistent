package com.github.fmaiassistent.exporter;

import com.github.fmaiassistent.linux.MemoryRegion;
import com.github.fmaiassistent.memory.ProcessMemoryReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuildSeasonStatsReaderTest {
    private static final int BUILD = 0x1234;
    private static final long PLAYER = 0x1_0000;
    private static final long BLOCK = 0x2_0000;
    private static final BuildSeasonStatsReader.Layout LAYOUT = new BuildSeasonStatsReader.Layout(
            0x20, 0x00, 0x08, 0x0c, 0x10, 0x14, 0x18, 0x1c, 0x20, 0x24, 100.0);

    @Test
    void decodesAValidatedCurrentSeasonBlockAndPreservesZeros() throws Exception {
        StubReader reader = new StubReader();
        reader.qword(PLAYER + 0x20, BLOCK);
        reader.qword(BLOCK, PLAYER);
        reader.integer(BLOCK + 0x08, 2025);
        reader.integer(BLOCK + 0x0c, 7);
        reader.integer(BLOCK + 0x10, 0);
        reader.integer(BLOCK + 0x14, 0);
        reader.integer(BLOCK + 0x18, 0);
        reader.integer(BLOCK + 0x1c, 0);
        reader.integer(BLOCK + 0x20, 0);
        reader.integer(BLOCK + 0x24, 0);

        SeasonStatsReader.Result result = new BuildSeasonStatsReader(Map.of(BUILD, LAYOUT))
                .read(reader, BUILD, PLAYER, LocalDate.of(2025, 8, 1));

        assertEquals(SeasonStatsReader.Result.State.AVAILABLE, result.state());
        assertEquals(0, result.stats().appearances());
        assertEquals(0, result.stats().goals());
        assertEquals(0.0, result.stats().averageRating());
    }

    @Test
    void rejectsIdentitySeasonAndRangeFailures() throws Exception {
        StubReader reader = validReader();
        reader.qword(BLOCK, PLAYER + 1);
        assertUnavailable(reader);

        reader = validReader();
        reader.integer(BLOCK + 0x08, 2024);
        assertUnavailable(reader);

        reader = validReader();
        reader.integer(BLOCK + 0x1c, 901);
        assertUnavailable(reader);
    }

    @Test
    void unreadIndividualFieldsBecomePartialWithoutBecomingZero() throws Exception {
        StubReader reader = validReader();
        reader.remove(BLOCK + 0x18);

        SeasonStatsReader.Result result = new BuildSeasonStatsReader(Map.of(BUILD, LAYOUT))
                .read(reader, BUILD, PLAYER, LocalDate.of(2025, 8, 1));

        assertEquals(SeasonStatsReader.Result.State.PARTIAL, result.state());
        assertNull(result.stats().minutes());
    }

    @Test
    void exactModuleHashProfileWinsOverBuildFallback() throws Exception {
        StubReader reader = validReader();
        BuildSeasonStatsReader.Layout fallback = new BuildSeasonStatsReader.Layout(
                0x28, 0x00, 0x08, 0x0c, 0x10, 0x14, 0x18, 0x1c, 0x20, 0x24, 100.0);
        BuildSeasonStatsReader readerWithProfiles = new BuildSeasonStatsReader(
                Map.of(BUILD, fallback),
                Map.of(new BuildSeasonStatsReader.ProfileKey(BUILD, "ABC"), LAYOUT));

        SeasonStatsReader.Result result = readerWithProfiles.read(
                reader, BUILD, PLAYER, LocalDate.of(2025, 8, 1),
                new com.github.fmaiassistent.linux.GamePluginIdentity("game_plugin.dll", "abc", 1));

        assertEquals(SeasonStatsReader.Result.State.AVAILABLE, result.state());
        assertEquals(10, result.stats().appearances());
    }

    private static StubReader validReader() {
        StubReader reader = new StubReader();
        reader.qword(PLAYER + 0x20, BLOCK);
        reader.qword(BLOCK, PLAYER);
        reader.integer(BLOCK + 0x08, 2025);
        reader.integer(BLOCK + 0x0c, 7);
        reader.integer(BLOCK + 0x10, 10);
        reader.integer(BLOCK + 0x14, 8);
        reader.integer(BLOCK + 0x18, 900);
        reader.integer(BLOCK + 0x1c, 2);
        reader.integer(BLOCK + 0x20, 3);
        reader.integer(BLOCK + 0x24, 725);
        return reader;
    }

    private static void assertUnavailable(StubReader reader) throws Exception {
        SeasonStatsReader.Result result = new BuildSeasonStatsReader(Map.of(BUILD, LAYOUT))
                .read(reader, BUILD, PLAYER, LocalDate.of(2025, 8, 1));
        assertEquals(SeasonStatsReader.Result.State.UNAVAILABLE, result.state());
    }

    private static final class StubReader implements ProcessMemoryReader {
        private final Map<Long, byte[]> memory = new HashMap<>();

        void qword(long address, long value) {
            memory.put(address, ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array());
        }

        void integer(long address, int value) {
            memory.put(address, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
        }

        void remove(long address) {
            memory.remove(address);
        }

        @Override
        public int pid() {
            return 1;
        }

        @Override
        public byte[] readBytes(long address, int size) throws IOException {
            for (Map.Entry<Long, byte[]> entry : memory.entrySet()) {
                long start = entry.getKey();
                byte[] bytes = entry.getValue();
                if (address >= start && address + size <= start + bytes.length) {
                    byte[] result = new byte[size];
                    System.arraycopy(bytes, (int) (address - start), result, 0, size);
                    return result;
                }
            }
            throw new IOException("unmapped test address");
        }

        @Override
        public List<MemoryRegion> maps() {
            return List.of();
        }

        @Override
        public void close() {
        }
    }
}
