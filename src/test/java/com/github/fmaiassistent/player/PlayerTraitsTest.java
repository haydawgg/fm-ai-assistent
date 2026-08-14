package com.github.fmaiassistent.player;

import com.github.fmaiassistent.linux.MemoryRegion;
import com.github.fmaiassistent.memory.ProcessMemoryReader;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerTraitsTest {
    @Test
    void readsPreferredMoveNamesFromPersonVector() {
        FakeReader reader = new FakeReader();
        long record = 0x4000;
        long vector = record + 0x40;
        long start = 0x9000;
        reader.putQword(vector, start);
        reader.putQword(vector + 8, start + 16);
        reader.putQword(vector + 16, start + 24);
        long first = 0xA000;
        long second = 0xB000;
        reader.putQword(start, first);
        reader.putQword(start + 8, second);
        putNamedObject(reader, first, "Places Shots");
        putNamedObject(reader, second, "Tries Killer Balls Often");

        String traits = PlayerTraits.read(reader, record);
        assertEquals("Places Shots; Tries Killer Balls Often", traits);
    }

    @Test
    void ignoresVectorWithUnknownStrings() {
        FakeReader reader = new FakeReader();
        long record = 0x4000;
        long vector = record + 0x40;
        long start = 0x9000;
        reader.putQword(vector, start);
        reader.putQword(vector + 8, start + 8);
        reader.putQword(vector + 16, start + 8);
        long item = 0xA000;
        reader.putQword(start, item);
        putNamedObject(reader, item, "Not A Real Trait");

        assertEquals("", PlayerTraits.read(reader, record));
    }

    @Test
    void catalogRecognisesCommonPreferredMoves() {
        assertTrue(PlayerTraits.isKnown("Places Shots"));
        assertTrue(PlayerTraits.isKnown("Gets Forward Whenever Possible"));
    }

    private static void putNamedObject(FakeReader reader, long object, String name) {
        long string = object + 0x80;
        reader.putQword(object + 0x20, string);
        byte[] chars = name.getBytes(StandardCharsets.US_ASCII);
        byte[] payload = new byte[4 + chars.length];
        ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).putInt(chars.length);
        System.arraycopy(chars, 0, payload, 4, chars.length);
        reader.memory.put(string, payload);
    }

    private static final class FakeReader implements ProcessMemoryReader {
        private final Map<Long, byte[]> memory = new HashMap<>();

        private void putQword(long address, long value) {
            memory.put(address, ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array());
        }

        @Override
        public int pid() {
            return 1;
        }

        @Override
        public List<MemoryRegion> maps() {
            return List.of();
        }

        @Override
        public byte[] readBytes(long address, int size) {
            for (Map.Entry<Long, byte[]> entry : memory.entrySet()) {
                long start = entry.getKey();
                byte[] data = entry.getValue();
                if (address >= start && address + size <= start + data.length) {
                    return java.util.Arrays.copyOfRange(data, (int) (address - start), (int) (address - start) + size);
                }
            }
            return new byte[size];
        }

        @Override
        public void close() {
        }
    }
}
