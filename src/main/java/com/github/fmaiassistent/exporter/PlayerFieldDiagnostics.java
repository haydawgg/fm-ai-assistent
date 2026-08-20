package com.github.fmaiassistent.exporter;

import com.github.fmaiassistent.linux.GamePluginIdentity;
import com.github.fmaiassistent.memory.ProcessMemoryReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Side-effect-free comparison harness for candidate native fields.
 *
 * <p>The normal exporter only needs {@link PlayerFieldReader}. This deeper
 * diagnostic seam lets a controlled live capture compare native reads with
 * known in-game or CSV values without publishing those reads to the snapshot.
 * It deliberately has no offset knowledge of its own.</p>
 */
public final class PlayerFieldDiagnostics {
    private PlayerFieldDiagnostics() {
    }

    public static Report inspect(ProcessMemoryReader reader, int build, GamePluginIdentity identity,
                                 PlayerFieldReader fieldReader, List<Sample> samples) {
        if (fieldReader == null || samples == null || samples.isEmpty()) {
            return new Report(0, 0, 0, 0, 0, List.of());
        }
        List<Observation> observations = new ArrayList<>();
        int available = 0;
        int partial = 0;
        int unavailable = 0;
        int matched = 0;
        for (Sample sample : samples) {
            CandidatePlayerFields fields;
            String error = "";
            try {
                fields = fieldReader.read(reader, build, sample.playerRecord(), identity);
            } catch (IOException | RuntimeException ex) {
                fields = CandidatePlayerFields.unknown();
                error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            }
            if (fields.state() == CandidatePlayerFields.State.AVAILABLE) available++;
            else if (fields.state() == CandidatePlayerFields.State.PARTIAL) partial++;
            else unavailable++;
            List<String> mismatches = compare(sample.expected(), fields);
            boolean expectedMatched = sample.expected() != null && error.isBlank() && mismatches.isEmpty();
            if (expectedMatched) matched++;
            observations.add(new Observation(sample.label(), sample.playerRecord(), fields,
                    expectedMatched, List.copyOf(mismatches), error));
        }
        return new Report(samples.size(), available, partial, unavailable, matched, List.copyOf(observations));
    }

    private static List<String> compare(CandidatePlayerFields expected, CandidatePlayerFields actual) {
        if (expected == null) return List.of();
        List<String> mismatches = new ArrayList<>();
        compare(mismatches, "source_uid", expected.sourceUid(), actual.sourceUid());
        compare(mismatches, "morale", expected.morale(), actual.morale());
        compare(mismatches, "condition", expected.condition(), actual.condition());
        compare(mismatches, "guide_value", expected.guideValue(), actual.guideValue());
        compare(mismatches, "transfer_value", expected.transferValue(), actual.transferValue());
        return mismatches;
    }

    private static void compare(List<String> mismatches, String field, Object expected, Object actual) {
        if (expected != null && !expected.equals(actual)) {
            mismatches.add(field + " expected=" + expected + " actual=" + actual);
        }
    }

    public record Sample(String label, long playerRecord, CandidatePlayerFields expected) {
        public Sample {
            label = label == null ? "" : label;
        }
    }

    public record Observation(String label, long playerRecord, CandidatePlayerFields fields,
                              boolean expectedMatched, List<String> mismatches, String error) {
        public Observation {
            mismatches = mismatches == null ? List.of() : List.copyOf(mismatches);
            error = error == null ? "" : error;
        }
    }

    public record Report(int sampleCount, int availableCount, int partialCount,
                         int unavailableCount, int expectedMatchedCount,
                         List<Observation> observations) {
        public Report {
            observations = observations == null ? List.of() : List.copyOf(observations);
        }
    }
}
