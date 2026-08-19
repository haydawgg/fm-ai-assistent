package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.repository.PlayerFilterCriteria;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerWorkspaceQueryTest {
    @Test
    void sessionClubScopesAnOtherwiseEmptyDeskFilter() {
        PlayerFilterCriteria effective = PlayerWorkspaceQuery.effectiveFilter(
                PlayerFilterCriteria.empty(), " Ajax ");

        assertEquals("Ajax", effective.club());
        assertTrue(effective.isClubOnly());
    }

    @Test
    void explicitFilterWinsOverSessionClub() {
        PlayerFilterCriteria requested = PlayerFilterCriteria.empty().withClub("PSV");

        assertEquals(requested, PlayerWorkspaceQuery.effectiveFilter(requested, "Ajax"));
    }
}
