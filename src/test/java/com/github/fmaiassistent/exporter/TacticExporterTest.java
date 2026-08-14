package com.github.fmaiassistent.exporter;

import com.github.fmaiassistent.linux.FmFormations;
import com.github.fmaiassistent.linux.MemoryRegion;
import com.github.fmaiassistent.memory.ProcessMemoryReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticExporterTest {
    @Test
    void decodesValidatedFormationAndSelectedPeople() throws IOException {
        FakeReader reader = new FakeReader();
        long manager = 0x1000;
        long block = 0x8000;
        reader.putI32(manager + TacticExporter.FORMATION_REL, 4);
        reader.putQword(manager + TacticExporter.SELECTION_POINTER_REL, block);
        Set<Long> people = new HashSet<>();
        for (int index = 0; index < 11; index++) {
            long person = 0x30000L + index;
            people.add(person);
            reader.putQword(block + TacticExporter.SELECTED_PERSONS_REL + index * 8L, person);
        }

        Optional<TacticExporter.Snapshot> snapshot = TacticExporter.decodeManager(reader, manager, people);
        assertTrue(snapshot.isPresent());
        assertEquals("4-1-2-3 DM Wide", snapshot.get().formation());
        assertEquals(List.of("GK", "DR", "DC", "DC", "DL", "DMC", "MC", "MC", "AMR", "AML", "ST"),
                snapshot.get().positions());
        assertEquals(11, snapshot.get().selectedPersonAddresses().size());
        assertEquals(0x30000L, snapshot.get().selectedPersonAddresses().get(0));
    }

    @Test
    void rejectsUnknownFormationCode() throws IOException {
        FakeReader reader = new FakeReader();
        reader.putI32(0x1000 + TacticExporter.FORMATION_REL, 999);
        assertTrue(TacticExporter.decodeManager(reader, 0x1000, Set.of(1L)).isEmpty());
    }

    @Test
    void formationFourHasElevenCanonicalSlots() {
        FmFormations.Shape shape = FmFormations.shape(4).orElseThrow();
        assertEquals(11, shape.positions().size());
        assertTrue(shape.slotText().startsWith("GK,,"));
    }

    private static final class FakeReader implements ProcessMemoryReader {
        private final Map<Long, byte[]> memory = new HashMap<>();

        private void putQword(long address, long value) {
            memory.put(address, ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array());
        }

        private void putI32(long address, int value) {
            memory.put(address, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
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
        public byte[] readBytes(long address, int size) throws IOException {
            byte[] data = memory.get(address);
            if (data == null || data.length < size) {
                throw new IOException("missing " + address);
            }
            return data;
        }

        @Override
        public void close() {
        }
    }
}
