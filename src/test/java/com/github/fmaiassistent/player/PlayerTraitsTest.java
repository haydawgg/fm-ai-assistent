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
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void readsTraitsFromOneRecordWindowInsteadOfOffsetHops() {
        CountingReader reader = new CountingReader();
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
        assertEquals(1, reader.recordWindowReads.get(), "record scan should be one readBytes window");
        assertTrue(reader.totalReads.get() < 113, "should not hop 113 record offsets");
    }

    @Test
    void prefersRecordPlus0x40OverEarlierJunkVector() {
        FakeReader reader = new FakeReader();
        long record = 0x4000;
        putTraitVector(reader, record - 0x1C0, 0x7000, 0xC000, "Places Shots");
        putTraitVector(reader, record + 0x40, 0x9000, 0xA000, "Tries Killer Balls Often");

        assertEquals("Tries Killer Balls Often", PlayerTraits.read(reader, record));
    }

    @Test
    void hopsOffsetsWhenWindowReadFails() {
        ThrowingWindowReader reader = new ThrowingWindowReader();
        long record = 0x4000;
        putTraitVector(reader, record + 0x40, 0x9000, 0xA000, "Places Shots");

        assertEquals("Places Shots", PlayerTraits.read(reader, record));
        assertTrue(reader.windowThrows.get() >= 1);
    }

    private static void putTraitVector(
            FakeReader reader, long vector, long start, long item, String name) {
        reader.putQword(vector, start);
        reader.putQword(vector + 8, start + 8);
        reader.putQword(vector + 16, start + 16);
        reader.putQword(start, item);
        putNamedObject(reader, item, name);
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

    private static class FakeReader implements ProcessMemoryReader {
        final Map<Long, byte[]> memory = new HashMap<>();

        void putQword(long address, long value) {
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
            byte[] out = new byte[size];
            for (Map.Entry<Long, byte[]> entry : memory.entrySet()) {
                long start = entry.getKey();
                byte[] data = entry.getValue();
                long overlapStart = Math.max(address, start);
                long overlapEnd = Math.min(address + (long) size, start + data.length);
                if (overlapStart < overlapEnd) {
                    int dest = (int) (overlapStart - address);
                    int src = (int) (overlapStart - start);
                    int len = (int) (overlapEnd - overlapStart);
                    System.arraycopy(data, src, out, dest, len);
                }
            }
            return out;
        }

        @Override
        public void close() {
        }
    }

    private static final class CountingReader extends FakeReader {
        private final AtomicInteger totalReads = new AtomicInteger();
        private final AtomicInteger recordWindowReads = new AtomicInteger();

        @Override
        public byte[] readBytes(long address, int size) {
            totalReads.incrementAndGet();
            if (size == PlayerTraits.WINDOW_SIZE) {
                recordWindowReads.incrementAndGet();
            }
            return super.readBytes(address, size);
        }
    }

    private static final class ThrowingWindowReader extends FakeReader {
        private final AtomicInteger windowThrows = new AtomicInteger();

        @Override
        public byte[] readBytes(long address, int size) {
            if (size == PlayerTraits.WINDOW_SIZE) {
                windowThrows.incrementAndGet();
                throw new IllegalStateException("unmapped trait window");
            }
            return super.readBytes(address, size);
        }
    }
}
