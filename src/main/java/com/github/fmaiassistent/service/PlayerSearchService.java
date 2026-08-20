package com.github.fmaiassistent.service;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.repository.PlayerFilterCriteria;
import org.springframework.stereotype.Service;

import java.util.List;

/** Shared player-search boundary for the desk, dossiers, and MCP adapters. */
@Service
public class PlayerSearchService {
    private final PlayerDatabaseService players;

    public PlayerSearchService(PlayerDatabaseService players) {
        this.players = players;
    }

    public List<PlayerEntity> find(PlayerFilterCriteria filter) {
        return players.findPlayerEntities(filter);
    }

    public List<PlayerEntity> page(PlayerFilterCriteria filter, int offset, int limit) {
        return players.findPlayerPage(filter, offset, limit);
    }

    public List<PlayerEntity> page(PlayerFilterCriteria filter, int offset, int limit,
                                    String sortKey, boolean descending) {
        return players.findPlayerPage(filter, offset, limit, sortKey, descending);
    }

    public long count(PlayerFilterCriteria filter) {
        return players.countPlayerEntities(filter);
    }
}
