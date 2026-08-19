package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.github.fmaiassistent.service.PlayerDatabaseService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Search seam for the shell; indexing and ranking stay out of AppShell. */
@Service
public class GlobalSearchService {
    private static final int MAX_RESULTS = 8;
    private final PlayerDatabaseService players;
    private final ClubDatabaseService clubs;
    private volatile List<Result> index = List.of();

    public GlobalSearchService(PlayerDatabaseService players, ClubDatabaseService clubs) {
        this.players = players;
        this.clubs = clubs;
    }

    public List<Result> search(String query) {
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() < 2) {
            return List.of();
        }
        ensureIndex();
        return index.stream()
                .filter(result -> result.name().toLowerCase(Locale.ROOT).contains(normalized)
                        || result.secondary().toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator.comparingInt(result -> rank(result, normalized)))
                .limit(MAX_RESULTS)
                .toList();
    }

    public void invalidate() {
        index = List.of();
    }

    private void ensureIndex() {
        if (!index.isEmpty()) {
            return;
        }
        synchronized (this) {
            if (!index.isEmpty()) {
                return;
            }
            List<Result> next = new ArrayList<>();
            for (String name : clubs.findNames()) {
                next.add(new Result(Kind.CLUB, name, "Club context", null));
            }
            for (PlayerEntity player : players.findAllPlayerEntities()) {
                if (player.getName() != null && !player.getName().isBlank()) {
                    String secondary = String.join(" · ", nonBlank(player.getClub()), nonBlank(PositionTextFormatter.format(player)));
                    next.add(new Result(Kind.PLAYER, player.getName(), secondary, player.getName()));
                }
            }
            index = List.copyOf(next);
        }
    }

    private static int rank(Result result, String query) {
        String name = result.name().toLowerCase(Locale.ROOT);
        int exact = name.equals(query) ? 0 : name.startsWith(query) ? 1 : 2;
        return exact * 10 + (result.kind() == Kind.PLAYER ? 0 : 1);
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? "" : value;
    }

    public enum Kind { PLAYER, CLUB }

    public record Result(Kind kind, String name, String secondary, String playerName) {
    }
}
