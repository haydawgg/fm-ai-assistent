package com.github.fmaiassistent.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerFilterCriteriaTest {

    @Test
    void nullMapsAreSafeForEmptyAndClubOnlyChecks() {
        PlayerFilterCriteria filter = new PlayerFilterCriteria(
                "", "", "", "", "", null, null, null, null, "",
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null);
        assertTrue(filter.isEmpty());
        assertFalse(filter.isClubOnly());
        assertTrue(filter.withClub("Test FC").isClubOnly());
    }

    @Test
    void chatSummaryListsActiveFilters() {
        PlayerFilterCriteria filter = PlayerFilterCriteria.empty()
                .withClub("Ajax");
        assertEquals("club Ajax", filter.chatSummary());
    }

    @Test
    void advancedPerformanceFiltersAreRepresentedInSummaryAndEmptyChecks() {
        PlayerFilterCriteria.Advanced advanced = new PlayerFilterCriteria.Advanced(
                true, true, null, null, null,
                PlayerFilterCriteria.LoanStatus.LOANED,
                PlayerFilterCriteria.ClubScope.PLAYING,
                10, null, 5, null, 900, null,
                2, null, 3, null, 7.0, null);
        PlayerFilterCriteria filter = PlayerFilterCriteria.empty().withAdvanced(advanced);

        assertFalse(filter.isEmpty());
        assertTrue(filter.chatSummary().contains("injured"));
        assertTrue(filter.chatSummary().contains("apps ≥ 10"));
        assertTrue(filter.chatSummary().contains("clubScope PLAYING"));
    }
}
