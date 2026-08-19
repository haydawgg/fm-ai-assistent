package com.github.fmaiassistent.football;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadAdvicePortTest {
    @Test
    void recommendationsOwnTheirReasonLists() {
        List<String> reasons = new ArrayList<>(List.of("high_wage"));
        SquadSellCandidate candidate = new SquadSellCandidate(
                1, "Ada", 30, "ST", 120, 130, 1000, null, null,
                4, -12, "sell", 45, reasons);
        reasons.add("mutated");

        assertEquals(List.of("high_wage"), candidate.reasons());
        assertTrue(new SquadWageHealth(100, 90L, 1.11).overBudget());
    }
}
