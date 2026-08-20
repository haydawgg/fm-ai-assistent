package com.github.fmaiassistent.linux;

import com.github.fmaiassistent.memory.ProcessMemoryReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GamePluginIdentityTest {
    @Test
    void detectsPathSizeAndSha256FromMappedModule() throws Exception {
        Path directory = Files.createTempDirectory("fm-ai-module-test");
        Path module = directory.resolve("game_plugin.dll");
        try {
            Files.write(module, new byte[]{1, 2, 3, 4});
            Reader reader = new Reader(module.toString());
            assertEquals(module.toString(), FmOffsets.gamePluginPath(reader).orElse(""));
            GamePluginIdentity identity = GamePluginIdentity.detect(reader);
            assertTrue(identity.isKnown());
            assertEquals(4, identity.size());
            assertEquals("9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a", identity.sha256());
        } finally {
            Files.deleteIfExists(module);
            Files.deleteIfExists(directory);
        }
    }

    private record Reader(String path) implements ProcessMemoryReader {
        @Override public int pid() { return 1; }
        @Override public byte[] readBytes(long address, int size) throws IOException { throw new IOException("unused"); }
        @Override public List<MemoryRegion> maps() { return List.of(new MemoryRegion(1, 2, "r-xp", 0, "", "", path)); }
        @Override public void close() { }
    }
}
