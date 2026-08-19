package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.repository.PlayerFilterCriteria;
import com.github.fmaiassistent.service.PlayerDatabaseService;

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

    long count();

    static PlayerWorkspaceQuery database(PlayerDatabaseService service) {
        return new DatabasePlayerWorkspaceQuery(service);
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
    public long count() {
        return service.countPlayers();
    }
}
