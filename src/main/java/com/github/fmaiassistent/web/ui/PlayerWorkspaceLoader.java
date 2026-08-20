package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.repository.PlayerFilterCriteria;
import com.github.fmaiassistent.service.PlayerDatabaseService;
import com.github.fmaiassistent.service.PlayerSearchService;

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

    List<PlayerEntity> loadPage(PlayerFilterCriteria filter, String sessionClub, int offset, int limit);

    default List<PlayerEntity> loadPage(PlayerFilterCriteria filter, String sessionClub, int offset, int limit,
                                        String sortKey, boolean descending) {
        return loadPage(filter, sessionClub, offset, limit);
    }

    long count(PlayerFilterCriteria filter, String sessionClub);

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
        return new DatabasePlayerWorkspaceLoader(PlayerWorkspaceQuery.database(service));
    }

    static PlayerWorkspaceLoader database(PlayerSearchService service) {
        return new DatabasePlayerWorkspaceLoader(PlayerWorkspaceQuery.database(service));
    }
}

final class DatabasePlayerWorkspaceLoader implements PlayerWorkspaceLoader {
    private final PlayerWorkspaceQuery query;

    DatabasePlayerWorkspaceLoader(PlayerWorkspaceQuery query) {
        this.query = query;
    }

    @Override
    public Result load(PlayerFilterCriteria filter, String sessionClub) {
        return new Result(query.findPage(filter, sessionClub, 0, 100), query.count(filter, sessionClub));
    }

    @Override
    public List<PlayerEntity> loadPage(PlayerFilterCriteria filter, String sessionClub, int offset, int limit) {
        return query.findPage(filter, sessionClub, offset, limit);
    }

    @Override
    public List<PlayerEntity> loadPage(PlayerFilterCriteria filter, String sessionClub, int offset, int limit,
                                       String sortKey, boolean descending) {
        return query.findPage(filter, sessionClub, offset, limit, sortKey, descending);
    }

    @Override
    public long count(PlayerFilterCriteria filter, String sessionClub) {
        return query.count(filter, sessionClub);
    }
}
