package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.repository.PlayerFilterCriteria;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PlayerWorkspaceLoaderTest {
    @Test
    void resultDistinguishesEmptyAndFilteredRows() {
        PlayerWorkspaceLoader.Result empty = new PlayerWorkspaceLoader.Result(List.of(), 4);
        assertTrue(empty.empty());
        assertTrue(empty.filtered());

        PlayerWorkspaceLoader.Result complete = new PlayerWorkspaceLoader.Result(
                List.of(PlayerEntity.fromExportRow(java.util.Map.of())), 1);
        assertFalse(complete.empty());
        assertFalse(complete.filtered());
    }

    @Test
    void resultDefensivelyCopiesRowsAndNormalizesInvalidTotals() {
        PlayerWorkspaceLoader.Result result = new PlayerWorkspaceLoader.Result(null, -1);
        assertEquals(List.of(), result.rows());
        assertEquals(0, result.totalCount());
    }

    @Test
    void loaderDelegatesFilteringAndCountingToTheQuerySeam() {
        PlayerEntity player = PlayerEntity.fromExportRow(java.util.Map.of("name", "Ada"));
        AtomicReference<PlayerFilterCriteria> receivedFilter = new AtomicReference<>();
        AtomicReference<String> receivedClub = new AtomicReference<>();
        PlayerWorkspaceQuery query = new PlayerWorkspaceQuery() {
            @Override
            public List<PlayerEntity> find(PlayerFilterCriteria filter, String sessionClub) {
                receivedFilter.set(filter);
                receivedClub.set(sessionClub);
                return List.of(player);
            }

            @Override
            public long count() {
                return 3;
            }
        };

        PlayerFilterCriteria filter = PlayerFilterCriteria.empty();
        PlayerWorkspaceLoader.Result result = new DatabasePlayerWorkspaceLoader(query).load(filter, "Ajax");

        assertEquals(filter, receivedFilter.get());
        assertEquals("Ajax", receivedClub.get());
        assertEquals(List.of(player), result.rows());
        assertEquals(3, result.totalCount());
    }
}
