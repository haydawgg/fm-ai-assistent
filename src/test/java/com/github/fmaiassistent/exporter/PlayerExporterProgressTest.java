package com.github.fmaiassistent.exporter;

import com.github.fmaiassistent.linux.MemoryRegion;
import com.github.fmaiassistent.memory.ProcessMemoryReader;
import com.github.fmaiassistent.service.LoadProgress;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerExporterProgressTest {
    private static final long SLOT_BASE = 0x1_0000;

    @Test
    void peopleExportReportsIncreasingDoneAndFinishesAtTotal() {
        int total = 2_500;
        List<LoadProgress> events = new ArrayList<>();
        new PlayerExporter().exportSlots(new FakeReader(), SLOT_BASE, total, new ArrayList<>(), events::add);

        assertFalse(events.isEmpty());
        assertEquals(0, events.getFirst().done());
        assertEquals(total, events.getFirst().total());
        assertEquals(LoadProgress.Phase.PEOPLE, events.getFirst().phase());

        long previous = -1;
        for (LoadProgress event : events) {
            assertTrue(event.done() >= previous);
            assertEquals(total, event.total());
            previous = event.done();
        }
        assertEquals(total, events.getLast().done());
        assertEquals(total, events.getLast().total());
    }

    private static final class FakeReader implements ProcessMemoryReader {
        @Override
        public int pid() {
            return 1;
        }

        @Override
        public byte[] readBytes(long address, int size) {
            return new byte[size];
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
