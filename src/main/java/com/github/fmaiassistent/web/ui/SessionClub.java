package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.service.AppSettingsService;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.vaadin.flow.component.combobox.ComboBox;

import java.util.List;

final class SessionClub {
    private SessionClub() {
    }

    static List<String> names(ClubDatabaseService clubs) {
        return clubs.findNames();
    }

    static String canonicalize(String saved, List<String> names) {
        if (saved == null || saved.isBlank()) {
            return "";
        }
        return names.stream()
                .filter(name -> name.equalsIgnoreCase(saved))
                .findFirst()
                .orElse("");
    }

    static String resolved(AppSettingsService settings, List<String> names) {
        return canonicalize(settings.sessionClub(), names);
    }

    static void prefill(ComboBox<String> combo, AppSettingsService settings, List<String> names) {
        combo.setItems(names);
        String match = resolved(settings, names);
        if (!match.isBlank()) {
            combo.setValue(match);
        }
    }
}
