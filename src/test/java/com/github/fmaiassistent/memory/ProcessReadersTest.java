package com.github.fmaiassistent.memory;

import com.github.fmaiassistent.linux.ProcessInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessReadersTest {
    @Test
    void acceptsOnlyTheExactFootballManagerExecutableName() {
        assertTrue(ProcessReaders.isFootballManagerProcess(new ProcessInfo(12, "fm.exe", "fm.exe")));
        assertTrue(ProcessReaders.isFootballManagerProcess(new ProcessInfo(12, "C:\\Games\\fm.exe", "fm.exe")));
        assertFalse(ProcessReaders.isFootballManagerProcess(new ProcessInfo(12, "fm-helper.exe", "fm.exe")));
        assertFalse(ProcessReaders.isFootballManagerProcess(new ProcessInfo(12, "", "fm.exe")));
    }

    @Test
    void resultReaderRejectsInvalidRangesBeforeCallingTheAdapter() {
        ProcessMemoryReader reader = new ProcessMemoryReader() {
            public int pid() { return 1; }
            public byte[] readBytes(long address, int size) {
                throw new AssertionError("adapter must not receive an invalid range");
            }
            public java.util.List<com.github.fmaiassistent.linux.MemoryRegion> maps() {
                return java.util.List.of();
            }
            public void close() { }
        };

        MemoryReadResult<byte[]> result = reader.readBytesResult(-1, 1);
        assertEquals(MemoryReadResult.State.ERROR, result.state());
    }
}
