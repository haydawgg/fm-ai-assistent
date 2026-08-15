package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.service.OpenRouterModelCatalog;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.server.Command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
        bind(model, catalog, selected, refreshFromNetwork, labels, List.of());
    }

    static void bind(
            ComboBox<String> model,
            OpenRouterModelCatalog catalog,
            String selected,
            boolean refreshFromNetwork,
            Map<String, String> labels,
            List<String> pinned) {
        apply(model, labels, catalog.cachedModels(), selected, pinned);
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
                firstNonBlank(model.getValue(), selected),
                pinned)));
    }

    static void apply(
            ComboBox<String> model,
            Map<String, String> labels,
            List<OpenRouterModelCatalog.Model> models,
            String selected) {
        apply(model, labels, models, selected, List.of());
    }

    static void apply(
            ComboBox<String> model,
            Map<String, String> labels,
            List<OpenRouterModelCatalog.Model> models,
            String selected,
            List<String> pinned) {
        labels.clear();
        LinkedHashSet<String> order = new LinkedHashSet<>();
        if (pinned != null) {
            for (String id : pinned) {
                if (id != null && !id.isBlank()) {
                    order.add(id.strip());
                }
            }
        }
        if (models != null) {
            for (OpenRouterModelCatalog.Model item : models) {
                String star = pinned != null && pinned.contains(item.id()) ? "★ " : "";
                labels.put(item.id(), star + item.label());
                order.add(item.id());
            }
        }
        String chosen = selected == null ? "" : selected.trim();
        if (!chosen.isBlank()) {
            labels.putIfAbsent(chosen, (pinned != null && pinned.contains(chosen) ? "★ " : "") + chosen);
            order.add(chosen);
        }
        model.setItemLabelGenerator(id -> labels.getOrDefault(id, id));
        model.setItems(new ArrayList<>(order));
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
