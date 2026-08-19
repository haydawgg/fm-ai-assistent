package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.PlayerEntity;

import java.util.List;
import java.util.Objects;

/** Pure selection state for player navigation and two-player comparison. */
final class PlayerWorkspaceSelection {
    private PlayerEntity selected;
    private PlayerEntity compareAnchor;
    private boolean awaitingCompare;

    PlayerEntity selected() {
        return selected;
    }

    PlayerEntity compareAnchor() {
        return compareAnchor;
    }

    boolean awaitingCompare() {
        return awaitingCompare;
    }

    void select(PlayerEntity player) {
        if (awaitingCompare && compareAnchor != null && player != null
                && !samePlayer(compareAnchor, player)) {
            awaitingCompare = false;
        }
        selected = player;
    }

    void startCompare() {
        if (selected != null) {
            compareAnchor = selected;
            awaitingCompare = true;
        }
    }

    void clearCompare() {
        compareAnchor = null;
        awaitingCompare = false;
    }

    void clear() {
        selected = null;
        clearCompare();
    }

    void reconcile(List<PlayerEntity> visible) {
        List<PlayerEntity> rows = visible == null ? List.of() : visible;
        selected = retain(selected, rows);
        compareAnchor = retain(compareAnchor, rows);
        if (compareAnchor == null) {
            awaitingCompare = false;
        }
    }

    private static PlayerEntity retain(PlayerEntity candidate, List<PlayerEntity> rows) {
        if (candidate == null) {
            return null;
        }
        return rows.stream()
                .filter(player -> samePlayer(player, candidate))
                .findFirst()
                .orElse(null);
    }

    private static boolean samePlayer(PlayerEntity left, PlayerEntity right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return Objects.equals(left.getId(), right.getId());
        }
        return false;
    }
}
