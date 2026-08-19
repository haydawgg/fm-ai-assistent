package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.repository.PlayerFilterCriteria;
import com.github.fmaiassistent.service.PlayerDatabaseService;

import java.util.List;

/**
 * Deep loading seam for the player workspace.
 *
 * <p>The view receives one immutable result containing both visible rows and
 * the unfiltered total. This keeps club scoping, filtering, and count
 * semantics out of Vaadin presentation code and gives tests a small seam for
 * loading, empty results, and failures.</p>
 */
interface PlayerWorkspaceLoader {
    Result load(PlayerFilterCriteria filter, String sessionClub);

    record Result(List<PlayerEntity> rows, long totalCount) {
        public Result {
            rows = rows == null ? List.of() : List.copyOf(rows);
            totalCount = Math.max(0, totalCount);
        }

        boolean empty() {
            return rows.isEmpty();
        }

        boolean filtered() {
            return rows.size() < totalCount;
        }
    }

    static PlayerWorkspaceLoader database(PlayerDatabaseService service) {
        return new DatabasePlayerWorkspaceLoader(service);
    }
}

final class DatabasePlayerWorkspaceLoader implements PlayerWorkspaceLoader {
    private final PlayerDatabaseService service;

    DatabasePlayerWorkspaceLoader(PlayerDatabaseService service) {
        this.service = service;
    }

    @Override
    public Result load(PlayerFilterCriteria filter, String sessionClub) {
        PlayerFilterCriteria requested = filter == null ? PlayerFilterCriteria.empty() : filter;
        PlayerFilterCriteria effective = PlayerWorkspaceQuery.effectiveFilter(requested, sessionClub);
        List<PlayerEntity> rows = effective.isEmpty()
                ? service.findAllPlayerEntities()
                : service.findPlayerEntities(effective);
        return new Result(rows, service.countPlayers());
    }
}
