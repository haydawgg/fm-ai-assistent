package com.github.fmaiassistent.exporter;

import com.github.fmaiassistent.linux.MemoryRegion;
import com.github.fmaiassistent.memory.ProcessMemoryReader;
import com.github.fmaiassistent.player.AttributeDefinitions;
import com.github.fmaiassistent.player.FieldDef;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerExporterClassifyTest {
    private static final long SLOT_BASE = 0x1_0000;
    private static final long RECORD = 0x2_0000;
    private static final int DUAL_PLAYER_STAFF_SHIFT = 0xF8;
    private static final int SOURCE_SIZE = Math.max(
            AttributeDefinitions.POSITION_FIELDS.stream().mapToInt(FieldDef::offset).max().orElseThrow(),
            AttributeDefinitions.VISIBLE_FIELDS.stream().mapToInt(FieldDef::offset).max().orElseThrow())
            - AttributeDefinitions.SOURCE_OBJECT_BASE_OFFSET + 1;

    @Test
    void keepsPlayerWithUnknownPa() throws Exception {
        FakeReader reader = new FakeReader();
        putSlot(reader, 0, RECORD);
        putAbilities(reader, RECORD, 0, 120, -1);
        putSource(reader, RECORD, 0, 18, 1, 50);
        putName(reader, RECORD, "Hidden PA");

        Run result = export(reader, 1);

        assertEquals(1, result.rows().size());
        assertEquals("Hidden PA", result.rows().getFirst().get("name"));
        assertEquals(120, ((Number) result.rows().getFirst().get("ca")).intValue());
        assertNull(result.rows().getFirst().get("pa"));
        assertEquals(0, result.skips().staff());
        assertEquals(0, result.skips().decodeError());
    }

    @Test
    void dropsStaffWithZeroPositions() throws Exception {
        FakeReader reader = new FakeReader();
        putSlot(reader, 0, RECORD);
        putAbilities(reader, RECORD, 0, 80, 80);
        putSource(reader, RECORD, 0, 0, 0, 50);
        putName(reader, RECORD, "Coach");

        Run result = export(reader, 1);

        assertTrue(result.rows().isEmpty());
        assertEquals(1, result.skips().staff());
        assertEquals(0, result.skips().decodeError());
    }

    @Test
    void keepsDualStaffShiftedPlayer() throws Exception {
        FakeReader reader = new FakeReader();
        putSlot(reader, 0, RECORD);
        putAbilities(reader, RECORD, 0, 0, 0);
        putAbilities(reader, RECORD, -DUAL_PLAYER_STAFF_SHIFT, 140, 160);
        putSource(reader, RECORD, -DUAL_PLAYER_STAFF_SHIFT, 18, 1, 50);
        putName(reader, RECORD, "Player Coach");

        Run result = export(reader, 1);

        assertEquals(1, result.rows().size());
        assertEquals("Player Coach", result.rows().getFirst().get("name"));
        assertEquals(140, ((Number) result.rows().getFirst().get("ca")).intValue());
        assertEquals(160, ((Number) result.rows().getFirst().get("pa")).intValue());
        assertEquals(0, result.skips().staff());
    }

    @Test
    void nullSlotPointerCountsAsEmptyNotDecodeError() throws Exception {
        FakeReader reader = new FakeReader();

        Run result = export(reader, 1);

        assertTrue(result.rows().isEmpty());
        assertEquals(1, result.skips().empty());
        assertEquals(0, result.skips().decodeError());
    }

    private static Run export(ProcessMemoryReader reader, int slots) throws Exception {
        Method method = PlayerExporter.class.getDeclaredMethod(
                "exportRange",
                ProcessMemoryReader.class,
                long.class,
                long.class,
                long.class,
                Map.class,
                List.class,
                PlayerExporter.SkipCounts.class);
        method.setAccessible(true);
        List<Map<String, Object>> rows = new ArrayList<>();
        PlayerExporter.SkipCounts skips = new PlayerExporter.SkipCounts();
        method.invoke(new PlayerExporter(), reader, SLOT_BASE, 0L, (long) slots, new ConcurrentHashMap<>(), rows, skips);
        return new Run(rows, skips.snapshot());
    }

    private static void putSlot(FakeReader reader, int index, long record) {
        reader.putU64(SLOT_BASE + index * 8L, record);
    }

    private static void putAbilities(FakeReader reader, long record, int shift, int ca, int pa) {
        reader.putI16(record + AttributeDefinitions.CURRENT_ABILITY_REL + shift, ca);
        reader.putI16(record + AttributeDefinitions.POTENTIAL_ABILITY_REL + shift, pa);
    }

    private static void putSource(FakeReader reader, long record, int shift, int goalkeeper, int otherPositions, int attributes) {
        long source = record + AttributeDefinitions.HISTORY_COPY_SOURCE_REL + shift;
        for (FieldDef field : AttributeDefinitions.POSITION_FIELDS) {
            int value = "Goalkeeper".equals(field.name()) ? goalkeeper : otherPositions;
            reader.putU8(source + field.offset() - AttributeDefinitions.SOURCE_OBJECT_BASE_OFFSET, value);
        }
        for (FieldDef field : AttributeDefinitions.VISIBLE_FIELDS) {
            reader.putU8(source + field.offset() - AttributeDefinitions.SOURCE_OBJECT_BASE_OFFSET, attributes);
        }
        reader.putU8(source + SOURCE_SIZE - 1, attributes);
    }

    private static void putName(FakeReader reader, long record, String name) {
        long nameAddress = 0x9_0000;
        reader.putU64(record + 0x40, nameAddress);
        reader.putFmString(nameAddress, name);
    }

    private record Run(List<Map<String, Object>> rows, PlayerExporter.SkipSnapshot skips) {
    }

    private static final class FakeReader implements ProcessMemoryReader {
        private final Map<Long, Byte> memory = new HashMap<>();

        void putU8(long address, int value) {
            memory.put(address, (byte) value);
        }

        void putI16(long address, int value) {
            memory.put(address, (byte) value);
            memory.put(address + 1, (byte) (value >>> 8));
        }

        void putU64(long address, long value) {
            for (int index = 0; index < Long.BYTES; index++) {
                memory.put(address + index, (byte) (value >>> (index * 8)));
            }
        }

        void putFmString(long address, String name) {
            byte[] chars = name.getBytes(StandardCharsets.US_ASCII);
            ByteBuffer buffer = ByteBuffer.allocate(4 + chars.length).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(chars.length);
            buffer.put(chars);
            byte[] data = buffer.array();
            for (int index = 0; index < data.length; index++) {
                memory.put(address + index, data[index]);
            }
        }

        @Override
        public int pid() {
            return 1;
        }

        @Override
        public byte[] readBytes(long address, int size) {
            byte[] bytes = new byte[size];
            for (int index = 0; index < size; index++) {
                Byte value = memory.get(address + index);
                if (value != null) {
                    bytes[index] = value;
                }
            }
            return bytes;
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
