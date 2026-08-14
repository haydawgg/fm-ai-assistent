package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import com.github.fmaiassistent.service.AppSettingsService;
import com.github.fmaiassistent.service.OpenRouterModelCatalog;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.PasswordField;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

final class SettingsDialog {
    private SettingsDialog() {
    }

    static void open(
            AppSettingsService settings,
            OpenRouterModelCatalog catalog,
            MoneyCurrency currentCurrency,
            Consumer<MoneyCurrency> onSaved) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Settings");
        dialog.setWidth("520px");
        dialog.setMaxWidth("calc(100vw - 32px)");
        dialog.getElement().getThemeList().add("professional-dialog");
        dialog.getElement().getThemeList().add("settings-dialog");

        Select<MoneyCurrency> currencySelect = new Select<>();
        currencySelect.setLabel("Currency");
        currencySelect.setItems(MoneyCurrency.POUND, MoneyCurrency.DOLLAR, MoneyCurrency.EURO);
        currencySelect.setItemLabelGenerator(MoneyCurrency::label);
        currencySelect.setValue(currentCurrency == null ? MoneyCurrency.POUND : currentCurrency);

        PasswordField apiKey = new PasswordField("OpenRouter API key");
        apiKey.setWidthFull();
        apiKey.setValue(settings.openRouterApiKey());
        apiKey.setPlaceholder("sk-or-... or leave empty to use MCP only");
        apiKey.setHelperText("From openrouter.ai/keys. Empty disables in-app chat; Claude or another MCP client can still use /mcp.");

        ComboBox<String> model = OpenRouterModelPicker.comboBox();
        model.setHelperText("Catalog refreshes from OpenRouter when this dialog opens. Type an id if a model is missing.");
        Map<String, String> labels = new LinkedHashMap<>();
        OpenRouterModelPicker.bind(model, catalog, settings.openRouterModel(), false, labels);
        Span catalogStatus = new Span("Refreshing OpenRouter models…");
        catalogStatus.addClassName("settings-path");
        UI ui = UI.getCurrent();
        catalog.refreshAsync().whenComplete((models, error) -> OpenRouterModelPicker.access(ui, () -> {
            if (!dialog.isOpened()) {
                return;
            }
            if (models != null && !models.isEmpty()) {
                OpenRouterModelPicker.apply(model, labels, models,
                        OpenRouterModelPicker.firstNonBlank(model.getValue(), settings.openRouterModel()));
                catalogStatus.setText(models.size() + " OpenRouter models · live catalog");
            } else {
                OpenRouterModelPicker.apply(model, labels, catalog.cachedModels(),
                        OpenRouterModelPicker.firstNonBlank(model.getValue(), settings.openRouterModel()));
                String detail = error == null ? catalog.lastError() : OpenRouterModelPicker.errorMessage(error);
                catalogStatus.setText(detail == null || detail.isBlank()
                        ? "Could not refresh OpenRouter models. Type a model id."
                        : "Could not refresh OpenRouter models: " + detail);
            }
        }));

        Span intro = new Span("Display currency and optional in-app chat via OpenRouter. Chat is unused if the key is empty.");
        intro.addClassName("settings-intro");
        Span file = new Span("Stored in " + settings.settingsPath());
        file.addClassName("settings-path");

        VerticalLayout layout = new VerticalLayout(intro, currencySelect, apiKey, model, catalogStatus, file);
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.addClassName("settings-content");
        dialog.add(layout);

        Button save = new Button("Save", VaadinIcon.CHECK.create(), event -> {
            MoneyCurrency currency = currencySelect.getValue() == null ? MoneyCurrency.POUND : currencySelect.getValue();
            settings.saveCurrency(currency);
            settings.saveOpenRouter(apiKey.getValue(), model.getValue());
            if (onSaved != null) {
                onSaved.accept(currency);
            }
            Notification saved = Notification.show("Settings saved", 2500, Notification.Position.TOP_CENTER);
            saved.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            saved.addClassName("app-toast");
            dialog.close();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", VaadinIcon.CLOSE_SMALL.create(), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }
}
