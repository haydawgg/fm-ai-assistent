package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.repository.PlayerFilterCriteria;
import com.github.fmaiassistent.service.PlayerDatabaseService;
import com.github.fmaiassistent.service.PlayerSearchService;

import java.util.List;

/**
 * Deep seam for the player workspace data boundary.
 *
 * <p>The view owns presentation; this port owns the rule that a desk filter
 * is constrained by the selected session club when the user has not chosen a
 * different club explicitly. Tests can replace the database adapter without
 * constructing Vaadin components.</p>
 */
interface PlayerWorkspaceQuery {
    List<PlayerEntity> find(PlayerFilterCriteria filter, String sessionClub);

    default List<PlayerEntity> findPage(PlayerFilterCriteria filter, String sessionClub, int offset, int limit) {
        List<PlayerEntity> rows = find(filter, sessionClub);
        if (offset >= rows.size()) return List.of();
        return rows.subList(offset, Math.min(rows.size(), offset + Math.max(1, limit)));
    }

    default List<PlayerEntity> findPage(PlayerFilterCriteria filter, String sessionClub, int offset, int limit,
                                        String sortKey, boolean descending) {
        return findPage(filter, sessionClub, offset, limit);
    }

    long count();

    default long count(PlayerFilterCriteria filter, String sessionClub) {
        return count();
    }

    static PlayerWorkspaceQuery database(PlayerDatabaseService service) {
        return new DatabasePlayerWorkspaceQuery(service);
    }

    static PlayerWorkspaceQuery database(PlayerSearchService service) {
        return new SearchServicePlayerWorkspaceQuery(service);
    }

    static PlayerFilterCriteria effectiveFilter(PlayerFilterCriteria filter, String sessionClub) {
        PlayerFilterCriteria requested = filter == null ? PlayerFilterCriteria.empty() : filter;
        if (sessionClub == null || sessionClub.isBlank()
                || (requested.club() != null && !requested.club().isBlank())) {
            return requested;
        }
        String club = sessionClub.strip();
        return requested.isEmpty() || requested.isClubOnly()
                ? PlayerFilterCriteria.clubOnly(club)
                : requested.withClub(club);
    }
}

final class DatabasePlayerWorkspaceQuery implements PlayerWorkspaceQuery {
    private final PlayerDatabaseService service;

    DatabasePlayerWorkspaceQuery(PlayerDatabaseService service) {
        this.service = service;
    }

    @Override
    public List<PlayerEntity> find(PlayerFilterCriteria filter, String sessionClub) {
        PlayerFilterCriteria requested = filter == null ? PlayerFilterCriteria.empty() : filter;
        PlayerFilterCriteria effective = PlayerWorkspaceQuery.effectiveFilter(requested, sessionClub);
        if (effective.isEmpty()) {
            return service.findAllPlayerEntities();
        }
        return service.findPlayerEntities(effective);
    }

    @Override
    public List<PlayerEntity> findPage(PlayerFilterCriteria filter, String sessionClub, int offset, int limit) {
        PlayerFilterCriteria effective = PlayerWorkspaceQuery.effectiveFilter(
                filter == null ? PlayerFilterCriteria.empty() : filter, sessionClub);
        return service.findPlayerPage(effective, offset, limit);
    }

    @Override
    public List<PlayerEntity> findPage(PlayerFilterCriteria filter, String sessionClub, int offset, int limit,
                                       String sortKey, boolean descending) {
        PlayerFilterCriteria effective = PlayerWorkspaceQuery.effectiveFilter(
                filter == null ? PlayerFilterCriteria.empty() : filter, sessionClub);
        return service.findPlayerPage(effective, offset, limit, sortKey, descending);
    }

    @Override
    public long count() {
        return service.countPlayers();
    }

    @Override
    public long count(PlayerFilterCriteria filter, String sessionClub) {
        PlayerFilterCriteria effective = PlayerWorkspaceQuery.effectiveFilter(
                filter == null ? PlayerFilterCriteria.empty() : filter, sessionClub);
        return service.countPlayerEntities(effective);
    }
}

final class SearchServicePlayerWorkspaceQuery implements PlayerWorkspaceQuery {
    private final PlayerSearchService service;

    SearchServicePlayerWorkspaceQuery(PlayerSearchService service) {
        this.service = service;
    }

    @Override
    public List<PlayerEntity> find(PlayerFilterCriteria filter, String sessionClub) {
        return service.find(PlayerWorkspaceQuery.effectiveFilter(filter, sessionClub));
    }

    @Override
    public List<PlayerEntity> findPage(PlayerFilterCriteria filter, String sessionClub, int offset, int limit) {
        return service.page(PlayerWorkspaceQuery.effectiveFilter(filter, sessionClub), offset, limit);
    }

    @Override
    public List<PlayerEntity> findPage(PlayerFilterCriteria filter, String sessionClub, int offset, int limit,
                                       String sortKey, boolean descending) {
        return service.page(PlayerWorkspaceQuery.effectiveFilter(filter, sessionClub), offset, limit,
                sortKey, descending);
    }

    @Override
    public long count(PlayerFilterCriteria filter, String sessionClub) {
        return service.count(PlayerWorkspaceQuery.effectiveFilter(filter, sessionClub));
    }

    @Override
    public long count() {
        return service.count(PlayerFilterCriteria.empty());
    }
}
