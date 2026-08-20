package com.github.fmaiassistent.exporter;

import com.github.fmaiassistent.linux.GamePluginIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerFieldDiagnosticsTest {
    @Test
    void comparesControlledExpectedValuesWithoutPublishingAnything() {
        CandidatePlayerFields actual = new CandidatePlayerFields(42L, 80, 91, 1000L, 2000L,
                CandidatePlayerFields.State.AVAILABLE);
        PlayerFieldDiagnostics.Report report = PlayerFieldDiagnostics.inspect(
                null, 2603, new GamePluginIdentity("game_plugin.dll", "abc", 1),
                (reader, build, record, identity) -> actual,
                List.of(new PlayerFieldDiagnostics.Sample("Alice", 0x1000, actual)));

        assertEquals(1, report.sampleCount());
        assertEquals(1, report.availableCount());
        assertEquals(1, report.expectedMatchedCount());
        assertTrue(report.observations().getFirst().mismatches().isEmpty());
    }

    @Test
    void recordsMismatchesAndReaderFailures() {
        CandidatePlayerFields expected = new CandidatePlayerFields(42L, 80, null, null, null,
                CandidatePlayerFields.State.PARTIAL);
        PlayerFieldDiagnostics.Report mismatch = PlayerFieldDiagnostics.inspect(
                null, 2603, GamePluginIdentity.unknown(),
                (reader, build, record, identity) -> CandidatePlayerFields.unknown(),
                List.of(new PlayerFieldDiagnostics.Sample("Bob", 0x2000, expected)));
        assertEquals(1, mismatch.unavailableCount());
        assertEquals(0, mismatch.expectedMatchedCount());
        assertEquals(2, mismatch.observations().getFirst().mismatches().size());

        PlayerFieldDiagnostics.Report failure = PlayerFieldDiagnostics.inspect(
                null, 2603, GamePluginIdentity.unknown(),
                (reader, build, record, identity) -> { throw new IllegalStateException("read failed"); },
                List.of(new PlayerFieldDiagnostics.Sample("Carol", 0x3000, null)));
        assertEquals("read failed", failure.observations().getFirst().error());
        assertEquals(1, failure.unavailableCount());
    }
}
