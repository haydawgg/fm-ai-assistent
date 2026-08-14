package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.service.OpenRouterModelCatalog;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.server.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class OpenRouterModelPicker {
    private OpenRouterModelPicker() {
    }

    static ComboBox<String> comboBox() {
        ComboBox<String> model = new ComboBox<>("OpenRouter model");
        model.setWidthFull();
        model.setAllowCustomValue(true);
        model.setClearButtonVisible(false);
        model.setPlaceholder("Search OpenRouter models");
        return model;
    }

    static void bind(
            ComboBox<String> model,
            OpenRouterModelCatalog catalog,
            String selected,
            boolean refreshFromNetwork,
            Map<String, String> labels) {
        apply(model, labels, catalog.cachedModels(), selected);
        model.addCustomValueSetListener(event -> {
            String id = event.getDetail() == null ? "" : event.getDetail().trim();
            if (id.isBlank()) {
                return;
            }
            labels.putIfAbsent(id, id);
            model.setItems(new ArrayList<>(labels.keySet()));
            model.setValue(id);
        });
        if (!refreshFromNetwork) {
            return;
        }
        UI ui = UI.getCurrent();
        catalog.refreshAsync().whenComplete((models, error) -> access(ui, () -> apply(
                model,
                labels,
                models == null || models.isEmpty() ? catalog.cachedModels() : models,
                firstNonBlank(model.getValue(), selected))));
    }

    static void apply(
            ComboBox<String> model,
            Map<String, String> labels,
            List<OpenRouterModelCatalog.Model> models,
            String selected) {
        labels.clear();
        if (models != null) {
            for (OpenRouterModelCatalog.Model item : models) {
                labels.put(item.id(), item.label());
            }
        }
        String chosen = selected == null ? "" : selected.trim();
        if (!chosen.isBlank()) {
            labels.putIfAbsent(chosen, chosen);
        }
        model.setItemLabelGenerator(id -> labels.getOrDefault(id, id));
        model.setItems(new ArrayList<>(labels.keySet()));
        model.setPlaceholder("Search OpenRouter models");
        if (!chosen.isBlank()) {
            model.setValue(chosen);
        }
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    static String errorMessage(Throwable cause) {
        if (cause == null) {
            return "";
        }
        Throwable current = cause;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    static void access(UI ui, Command action) {
        if (ui == null) {
            return;
        }
        try {
            ui.access(action);
        } catch (UIDetachedException ignored) {
        }
    }
}
