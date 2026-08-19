package com.github.fmaiassistent.football;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferShortlistQueryTest {
    @Test
    void candidateResultsAreImmutableAtTheDomainSeam() {
        List<String> signals = new ArrayList<>(List.of("natural_position"));
        TransferShortlistCandidate candidate = new TransferShortlistCandidate(
                1, 82.5, "Ada", 20, "NL", "Club", 18, null,
                120, 160, 40, null, 1000, "high", false, false, false, signals);

        signals.add("mutated");

        assertEquals(List.of("natural_position"), candidate.signals());
        assertThrows(UnsupportedOperationException.class, () -> candidate.signals().clear());
    }

    @Test
    void queryKeepsUnknownFiltersUnknown() {
        TransferShortlistQuery query = new TransferShortlistQuery("Club", null, null, null, null, null, null, null);

        assertEquals("Club", query.managingClub());
        assertEquals(null, query.maxAskingPrice());
        assertEquals(null, query.maxWeeklySalary());
    }
}
