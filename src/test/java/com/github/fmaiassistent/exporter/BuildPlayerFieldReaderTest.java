package com.github.fmaiassistent.exporter;

import com.github.fmaiassistent.linux.MemoryRegion;
import com.github.fmaiassistent.linux.GamePluginIdentity;
import com.github.fmaiassistent.memory.ProcessMemoryReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuildPlayerFieldReaderTest {
    @Test
    void readsExplicitProfileUsingDocumentedWidths() {
        int build = 2603;
        long player = 0x10000;
        GamePluginIdentity identity = new GamePluginIdentity("game_plugin.dll", "abc", 1);
        BuildPlayerFieldReader.Layout layout = new BuildPlayerFieldReader.Layout(0x0c, 0x26c, 0x258, 0x234, 0x238);
        StubReader reader = new StubReader();
        reader.u32(player + 0x0c, 1234);
        reader.u8(player + 0x26c, 87);
        reader.u16(player + 0x258, 92);
        reader.u32(player + 0x234, 1_000_000);
        reader.u32(player + 0x238, 2_000_000);

        CandidatePlayerFields fields = new BuildPlayerFieldReader(
                Map.of(), Map.of(new BuildSeasonStatsReader.ProfileKey(build, "abc"), layout))
                .read(reader, build, player, identity);

        assertEquals(CandidatePlayerFields.State.AVAILABLE, fields.state());
        assertEquals(1234L, fields.sourceUid());
        assertEquals(87, fields.morale());
        assertEquals(92, fields.condition());
        assertEquals(2_000_000L, fields.transferValue());
    }

    @Test
    void unsupportedProfileIsUnknownRatherThanGuessing() {
        CandidatePlayerFields fields = new BuildPlayerFieldReader().read(
                new StubReader(), 2603, 0x10000, GamePluginIdentity.unknown());
        assertEquals(CandidatePlayerFields.State.UNAVAILABLE, fields.state());
        assertNull(fields.morale());
    }

    @Test
    void invalidCandidateFieldDoesNotDiscardOtherValidatedFields() {
        CandidatePlayerFields fields = new CandidatePlayerFields(
                123L, 101, 88, 1_000L, 2_000L, CandidatePlayerFields.State.AVAILABLE);

        assertEquals(CandidatePlayerFields.State.PARTIAL, fields.state());
        assertNull(fields.morale());
        assertEquals(88, fields.condition());
        assertEquals(2_000L, fields.transferValue());
    }

    private static final class StubReader implements ProcessMemoryReader {
        private final Map<Long, byte[]> memory = new HashMap<>();
        void u8(long address, int value) { memory.put(address, new byte[]{(byte) value}); }
        void u16(long address, int value) { memory.put(address, bytes(2, value)); }
        void u32(long address, long value) { memory.put(address, bytes(4, value)); }
        private static byte[] bytes(int size, long value) {
            ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
            if (size == 2) buffer.putShort((short) value); else buffer.putInt((int) value);
            return buffer.array();
        }
        @Override public int pid() { return 1; }
        @Override public byte[] readBytes(long address, int size) throws IOException {
            for (Map.Entry<Long, byte[]> entry : memory.entrySet()) {
                if (address >= entry.getKey() && address + size <= entry.getKey() + entry.getValue().length) {
                    byte[] result = new byte[size];
                    System.arraycopy(entry.getValue(), (int) (address - entry.getKey()), result, 0, size);
                    return result;
                }
            }
            throw new IOException("unmapped");
        }
        @Override public List<MemoryRegion> maps() { return List.of(); }
        @Override public void close() { }
    }
}
