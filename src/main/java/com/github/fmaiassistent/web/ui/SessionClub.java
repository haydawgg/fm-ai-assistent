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

    static String resolved(AppSettingsService settings, List<String> names) {
        String saved = settings.sessionClub();
        if (saved.isBlank()) {
            return "";
        }
        return names.stream()
                .filter(name -> name.equalsIgnoreCase(saved))
                .findFirst()
                .orElse("");
    }

    static void prefill(ComboBox<String> combo, AppSettingsService settings, List<String> names) {
        combo.setItems(names);
        String match = resolved(settings, names);
        if (!match.isBlank()) {
            combo.setValue(match);
        }
    }
}
