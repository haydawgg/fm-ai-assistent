package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.service.AppSettingsService;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.github.fmaiassistent.service.OpenRouterModelCatalog;
import com.github.fmaiassistent.service.PlayerDatabaseService;
import com.github.fmaiassistent.service.RamLoadCoordinator;
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
import com.vaadin.flow.component.textfield.PasswordField;

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class OnboardingWizard {
    private OnboardingWizard() {
    }

    static void openIfNeeded(
            AppSettingsService settings,
            ClubDatabaseService clubs,
            PlayerDatabaseService players,
            RamLoadCoordinator ramLoad,
            OpenRouterModelCatalog catalog) {
        if (settings.onboardingComplete()) {
            return;
        }
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Set up FM AI");
        dialog.setWidth("480px");
        dialog.setMaxWidth("calc(100vw - 32px)");
        dialog.getElement().getThemeList().add("professional-dialog");
        dialog.setCloseOnOutsideClick(false);

        ComboBox<String> club = new ComboBox<>("Your club");
        club.setWidthFull();
        List<String> names = SessionClub.names(clubs);
        club.setItems(names);
        club.setAllowCustomValue(true);
        SessionClub.prefill(club, settings, names);

        PasswordField apiKey = new PasswordField("OpenRouter API key");
        apiKey.setWidthFull();
        apiKey.setValue(settings.openRouterApiKey());
        apiKey.setPlaceholder("sk-or-...");

        Span status = new Span("Pick your club, load RAM with FM26 running, then test the key.");
        status.addClassName("settings-intro");

        Button load = new Button("Load from RAM", VaadinIcon.DATABASE.create(), event -> RamLoadUi.start(ramLoad, event.getSource()));
        load.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button test = new Button("Test key", VaadinIcon.CONNECT.create(), event -> {
            status.setText("Testing…");
            String key = apiKey.getValue();
            CompletableFuture.supplyAsync(() -> catalog.probe(key))
                    .whenComplete((result, error) -> OpenRouterModelPicker.access(UI.getCurrent(), () -> {
                        OpenRouterModelCatalog.ProbeResult probe = result != null
                                ? result
                                : new OpenRouterModelCatalog.ProbeResult(false, OpenRouterModelPicker.errorMessage(error));
                        status.setText(probe.message());
                    }));
        });

        Button finish = new Button("Start chatting", event -> {
            if (club.getValue() != null && !club.getValue().isBlank()) {
                settings.saveSessionClub(club.getValue());
            }
            if (apiKey.getValue() != null && !apiKey.getValue().isBlank()) {
                settings.saveOpenRouter(apiKey.getValue(), settings.openRouterModel());
            }
            settings.saveOnboardingComplete(true);
            dialog.close();
            UI ui = UI.getCurrent();
            if (ui != null) {
                ui.navigate("chat");
            }
            Notification.show("Setup saved", 2000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        finish.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button skip = new Button("Skip", event -> {
            settings.saveOnboardingComplete(true);
            dialog.close();
        });
        skip.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        VerticalLayout layout = new VerticalLayout(status, club, load, apiKey, test);
        layout.setPadding(false);
        layout.setSpacing(true);
        dialog.add(layout);
        dialog.getFooter().add(skip, finish);
        dialog.open();
    }
}
