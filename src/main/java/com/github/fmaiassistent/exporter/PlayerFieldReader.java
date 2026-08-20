package com.github.fmaiassistent.exporter;

import com.github.fmaiassistent.linux.GamePluginIdentity;
import com.github.fmaiassistent.memory.ProcessMemoryReader;

import java.io.IOException;

/** Deep seam for optional build-specific native player fields. */
@FunctionalInterface
public interface PlayerFieldReader {
    CandidatePlayerFields read(ProcessMemoryReader reader, int build, long playerRecord,
                                GamePluginIdentity identity) throws IOException;

    static PlayerFieldReader unsupported() {
        return (reader, build, playerRecord, identity) -> CandidatePlayerFields.unknown();
    }
}
