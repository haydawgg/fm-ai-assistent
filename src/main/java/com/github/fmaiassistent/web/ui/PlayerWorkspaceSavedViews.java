package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.repository.PlayerFilterCriteria;
import com.github.fmaiassistent.service.AppSettingsService;

import java.util.List;
import java.util.Optional;

/** Deep state seam for saved player-workspace views. */
final class PlayerWorkspaceSavedViews {
    private final AppSettingsService settings;

    PlayerWorkspaceSavedViews(AppSettingsService settings) {
        this.settings = settings;
    }

    List<String> names() {
        return settings.playerViews().stream().map(SavedPlayerView::name).toList();
    }

    Optional<SavedPlayerView> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return settings.playerViews().stream()
                .filter(view -> view.name().equalsIgnoreCase(name))
                .findFirst();
    }

    void save(String name, PlayerFilterCriteria filter, boolean showAllColumns) {
        settings.savePlayerView(new SavedPlayerView(name, filter, showAllColumns));
    }

    void delete(String name) {
        settings.deletePlayerView(name);
    }
}
