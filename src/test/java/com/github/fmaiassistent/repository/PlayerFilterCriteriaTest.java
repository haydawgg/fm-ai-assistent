package com.github.fmaiassistent.repository;

import org.junit.jupiter.api.Test;

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
}
