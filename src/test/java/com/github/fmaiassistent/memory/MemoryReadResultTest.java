package com.github.fmaiassistent.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemoryReadResultTest {
    @Test
    void keepsKnownUnknownAndErrorDistinct() {
        assertTrue(MemoryReadResult.known(new byte[]{0}).known());
        assertEquals(MemoryReadResult.State.UNKNOWN, MemoryReadResult.unknown("missing").state());
        assertEquals(MemoryReadResult.State.ERROR, MemoryReadResult.error("denied").state());
        assertNull(MemoryReadResult.unknown("missing").value());
    }

    @Test
    void readerAdapterConvertsReadFailuresToExplicitErrors() {
        ProcessMemoryReader reader = new ProcessMemoryReader() {
            public int pid() { return 1; }
            public byte[] readBytes(long address, int size) throws java.io.IOException {
                throw new java.io.IOException("gone");
            }
            public java.util.List<com.github.fmaiassistent.linux.MemoryRegion> maps() { return java.util.List.of(); }
            public void close() { }
        };
        MemoryReadResult<byte[]> result = reader.readBytesResult(1, 1);
        assertEquals(MemoryReadResult.State.ERROR, result.state());
        assertEquals("gone", result.message());
    }
}
