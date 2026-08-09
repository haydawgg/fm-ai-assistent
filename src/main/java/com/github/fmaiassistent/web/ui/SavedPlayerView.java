package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.repository.PlayerFilterCriteria;

public record SavedPlayerView(
        String name,
        PlayerFilterCriteria filter,
        boolean showAllColumns) {
}
