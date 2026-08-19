package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import com.github.fmaiassistent.service.AppSettingsService;
import com.github.fmaiassistent.service.OpenRouterModelCatalog;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

        ComboBox<String> fallback = OpenRouterModelPicker.comboBox();
        fallback.setLabel("Add fallback model");
        fallback.setHelperText("Tried in order if the primary fails.");
        Map<String, String> fallbackLabels = new LinkedHashMap<>();
        List<String> fallbackModels = new ArrayList<>(settings.openRouterFallbackModels());
        Div fallbackList = new Div();
        fallbackList.setWidthFull();
        Runnable refreshFallbacks = () -> refreshFallbackList(fallbackList, fallbackModels, fallbackLabels);

        ComboBox<String> model = OpenRouterModelPicker.comboBox();
        model.setHelperText("Catalog refreshes from OpenRouter when this dialog opens. Type an id if a model is missing.");
        Map<String, String> labels = new LinkedHashMap<>();
        OpenRouterModelPicker.bind(model, catalog, settings.openRouterModel(), false, labels);
        OpenRouterModelPicker.bind(fallback, catalog, "", false, fallbackLabels);
        Button addFallback = new Button("Add", event -> {
            String id = fallback.getValue();
            if (id != null && !id.isBlank() && !fallbackModels.contains(id.strip())) {
                fallbackModels.add(id.strip());
                refreshFallbacks.run();
            }
            fallback.clear();
        });
        addFallback.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout fallbackBar = new HorizontalLayout(fallback, addFallback);
        fallbackBar.setWidthFull();
        fallbackBar.setFlexGrow(1, fallback);
        fallbackBar.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.END);
        refreshFallbacks.run();
        Span catalogStatus = new Span("Refreshing OpenRouter models…");
        catalogStatus.addClassName("settings-path");
        UI ui = UI.getCurrent();
        UiAsync.submit(
                ui,
                () -> catalog.refreshAsync().join(),
                models -> {
                    if (!dialog.isOpened()) {
                        return;
                    }
                    if (models != null && !models.isEmpty()) {
                        OpenRouterModelPicker.apply(model, labels, models,
                                OpenRouterModelPicker.firstNonBlank(model.getValue(), settings.openRouterModel()));
                        OpenRouterModelPicker.apply(fallback, fallbackLabels, models, fallback.getValue());
                        catalogStatus.setText(models.size() + " OpenRouter models · live catalog");
                    } else {
                        applyCachedModels(model, fallback, labels, fallbackLabels, catalog, settings, catalogStatus);
                    }
                    refreshFallbacks.run();
                },
                error -> {
                    if (!dialog.isOpened()) {
                        return;
                    }
                    applyCachedModels(model, fallback, labels, fallbackLabels, catalog, settings, catalogStatus);
                    refreshFallbacks.run();
                });

        NumberField dailyCap = new NumberField("Daily spend cap (USD)");
        dailyCap.setWidthFull();
        dailyCap.setMin(0);
        dailyCap.setStep(0.5);
        dailyCap.setValue(settings.dailySpendCapUsd());
        dailyCap.setHelperText("0 or empty means no cap. Estimates use catalog prompt rates.");

        NumberField topP = new NumberField("Top-p (advanced)");
        topP.setWidthFull();
        topP.setMin(0.05);
        topP.setMax(1);
        topP.setStep(0.05);
        topP.setValue(settings.chatTopP());
        topP.setHelperText("Leave empty for the model default. Lower values make replies more focused.");

        TextArea instructions = new TextArea("Custom instructions");
        instructions.setWidthFull();
        instructions.setMinHeight("6em");
        instructions.setValue(settings.chatInstructions());
        instructions.setPlaceholder("Answer in Dutch. Always compare 3 options. Never suggest over budget.");
        instructions.setHelperText("Injected into the system prompt for every in-app chat turn.");

        Checkbox notify = new Checkbox("Notify when a reply finishes in another tab");
        notify.setValue(Boolean.TRUE.equals(settings.desktopNotify()));
        Span notifyHint = new Span("Asks the browser the first time a reply finishes while this tab is hidden.");
        notifyHint.addClassName("settings-path");

        Span testStatus = new Span("");
        testStatus.addClassName("settings-path");
        Button test = new Button("Test connection", VaadinIcon.CONNECT.create(), event -> {
            testStatus.setText("Testing…");
            String key = apiKey.getValue();
            UiAsync.submit(
                    ui,
                    () -> catalog.probe(key),
                    probe -> showProbeResult(testStatus, probe),
                    error -> showProbeResult(testStatus,
                            new OpenRouterModelCatalog.ProbeResult(false, OpenRouterModelPicker.errorMessage(error))));
        });
        test.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Span intro = new Span("Display currency and optional in-app chat via OpenRouter. Chat is unused if the key is empty.");
        intro.addClassName("settings-intro");
        Span file = new Span("Preferences stored in " + settings.settingsPath()
                + ". On Windows, the API key is protected with Windows DPAPI.");
        file.addClassName("settings-path");

        Button onboarding = new Button("Run setup again", VaadinIcon.ROCKET.create(), event -> {
            settings.saveOnboardingComplete(false);
            dialog.close();
            UI current = UI.getCurrent();
            if (current != null) {
                current.getPage().reload();
            }
        });
        onboarding.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        TextField promptName = new TextField("Saved prompt name");
        promptName.setWidthFull();
        TextArea promptText = new TextArea("Prompt");
        promptText.setWidthFull();
        promptText.setMinHeight("4em");
        Div promptList = new Div();
        Button savePrompt = new Button("Save prompt", event -> {
            try {
                settings.saveChatPrompt(new SavedChatPrompt(promptName.getValue(), promptText.getValue()));
                promptName.clear();
                promptText.clear();
                refreshPromptList(settings, promptList);
            } catch (IllegalArgumentException error) {
                UiFeedback.error(error.getMessage(), 2500, Notification.Position.TOP_CENTER);
            }
        });
        savePrompt.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refreshPromptList(settings, promptList);

        VerticalLayout layout = new VerticalLayout(
                intro, currencySelect, apiKey, test, testStatus, model, fallbackBar, fallbackList, catalogStatus,
                dailyCap, topP, notify, notifyHint, instructions, promptName, promptText, savePrompt, promptList, onboarding, file);
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.addClassName("settings-content");
        dialog.add(layout);

        Button save = new Button("Save", VaadinIcon.CHECK.create(), event -> {
            MoneyCurrency currency = currencySelect.getValue() == null ? MoneyCurrency.POUND : currencySelect.getValue();
            settings.saveCurrency(currency);
            settings.saveOpenRouter(apiKey.getValue(), model.getValue());
            settings.saveOpenRouterFallbackModels(fallbackModels);
            settings.saveDailySpendCapUsd(dailyCap.getValue());
            settings.saveChatTopP(topP.getValue());
            settings.saveDesktopNotify(notify.getValue());
            settings.saveChatInstructions(instructions.getValue());
            if (onSaved != null) {
                onSaved.accept(currency);
            }
            Notification saved = UiFeedback.success("Settings saved", 2500, Notification.Position.TOP_CENTER);
            saved.addClassName("app-toast");
            dialog.close();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", VaadinIcon.CLOSE_SMALL.create(), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private static void applyCachedModels(
            ComboBox<String> model,
            ComboBox<String> fallback,
            Map<String, String> labels,
            Map<String, String> fallbackLabels,
            OpenRouterModelCatalog catalog,
            AppSettingsService settings,
            Span catalogStatus) {
        OpenRouterModelPicker.apply(model, labels, catalog.cachedModels(),
                OpenRouterModelPicker.firstNonBlank(model.getValue(), settings.openRouterModel()));
        OpenRouterModelPicker.apply(fallback, fallbackLabels, catalog.cachedModels(), fallback.getValue());
        String detail = catalog.lastError();
        catalogStatus.setText(detail == null || detail.isBlank()
                ? "Could not refresh OpenRouter models. Type a model id."
                : "Could not refresh OpenRouter models: " + detail);
    }

    private static void showProbeResult(Span status, OpenRouterModelCatalog.ProbeResult probe) {
        status.setText(probe.message());
        Notification notice = probe.ok()
                ? UiFeedback.success(probe.message(), 2800, Notification.Position.TOP_CENTER)
                : UiFeedback.error(probe.message(), 2800, Notification.Position.TOP_CENTER);
    }

    private static void refreshFallbackList(Div list, List<String> models, Map<String, String> labels) {
        list.removeAll();
        for (int index = 0; index < models.size(); index++) {
            String id = models.get(index);
            int current = index;
            Span label = new Span((index + 1) + ". " + labels.getOrDefault(id, id));
            Button up = new Button(VaadinIcon.ARROW_UP.create(), event -> {
                if (current == 0) {
                    return;
                }
                models.set(current, models.set(current - 1, id));
                refreshFallbackList(list, models, labels);
            });
            Button down = new Button(VaadinIcon.ARROW_DOWN.create(), event -> {
                if (current >= models.size() - 1) {
                    return;
                }
                models.set(current, models.set(current + 1, id));
                refreshFallbackList(list, models, labels);
            });
            Button remove = new Button(VaadinIcon.TRASH.create(), event -> {
                models.remove(current);
                refreshFallbackList(list, models, labels);
            });
            up.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            down.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            up.getElement().setAttribute("aria-label", "Move fallback model up");
            down.getElement().setAttribute("aria-label", "Move fallback model down");
            remove.getElement().setAttribute("aria-label", "Remove fallback model");
            HorizontalLayout row = new HorizontalLayout(label, up, down, remove);
            row.setWidthFull();
            row.setFlexGrow(1, label);
            list.add(row);
        }
    }

    private static void refreshPromptList(AppSettingsService settings, Div promptList) {
        promptList.removeAll();
        for (SavedChatPrompt prompt : settings.chatPrompts()) {
            Button delete = new Button("Delete", event -> {
                settings.deleteChatPrompt(prompt.name());
                refreshPromptList(settings, promptList);
            });
            delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            Span label = new Span(prompt.name());
            HorizontalLayout row = new HorizontalLayout(label, delete);
            row.setWidthFull();
            row.setFlexGrow(1, label);
            promptList.add(row);
        }
    }
}
