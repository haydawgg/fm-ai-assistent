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
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.PasswordField;

import java.util.List;
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
        dialog.setHeaderTitle("Welcome to FM AI");
        dialog.setWidth("480px");
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

        Span status = new Span("You can complete setup now or return to these steps later from Settings.");
        status.addClassName("settings-intro");

        Span progress = new Span();
        progress.addClassName("onboarding-progress");

        Button load = new Button("Load from RAM", VaadinIcon.DATABASE.create(), event -> RamLoadUi.start(ramLoad, event.getSource()));
        load.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button test = new Button("Test key", VaadinIcon.CONNECT.create(), event -> {
            status.setText("Testing…");
            String key = apiKey.getValue();
            UI ui = UI.getCurrent();
            UiAsync.submit(
                    ui,
                    () -> catalog.probe(key),
                    result -> status.setText(result.message()),
                    error -> status.setText(OpenRouterModelPicker.errorMessage(error)));
        });

        Button finish = new Button("Finish setup", event -> {
            String canonical = SessionClub.canonicalize(club.getValue(), names);
            if (canonical.isBlank() && club.getValue() != null && !club.getValue().isBlank() && !names.isEmpty()) {
                Notification.show("Pick a club from the list after loading RAM", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            if (!canonical.isBlank()) {
                settings.saveSessionClub(canonical);
            }
            if (apiKey.getValue() != null && !apiKey.getValue().isBlank()) {
                settings.saveOpenRouter(apiKey.getValue(), settings.openRouterModel());
            }
            settings.saveOnboardingComplete(true);
            dialog.close();
            UI ui = UI.getCurrent();
            if (ui != null) {
                ui.navigate("");
            }
            Notification.show("Setup saved", 2000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        finish.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button skip = new Button("Skip for now", event -> {
            settings.saveOnboardingComplete(true);
            dialog.close();
        });
        skip.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Span clubTitle = stepTitle("1", "Choose your club", "This focuses every squad, contract, and recruitment screen.");
        VerticalLayout clubStep = new VerticalLayout(clubTitle, club);
        clubStep.setPadding(false);
        clubStep.setSpacing(true);

        Span loadTitle = stepTitle("2", "Load your FM26 snapshot", "Keep Football Manager running with a save open, then load the current data.");
        VerticalLayout loadStep = new VerticalLayout(loadTitle, load);
        loadStep.setPadding(false);
        loadStep.setSpacing(true);

        Span aiTitle = stepTitle("3", "Enable optional AI chat", "Use OpenRouter for in-app advice. You can still use the player and squad tools without a key.");
        VerticalLayout aiStep = new VerticalLayout(aiTitle, apiKey, test);
        aiStep.setPadding(false);
        aiStep.setSpacing(true);

        VerticalLayout stepContent = new VerticalLayout();
        stepContent.setPadding(false);
        stepContent.setSpacing(true);
        VerticalLayout layout = new VerticalLayout(progress, status, stepContent);
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.addClassName("onboarding-content");
        dialog.add(layout);

        Button back = new Button("Back");
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button next = new Button("Continue");
        next.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        final int[] step = {0};
        Runnable renderStep = () -> {
            stepContent.removeAll();
            stepContent.add(switch (step[0]) {
                case 0 -> clubStep;
                case 1 -> loadStep;
                default -> aiStep;
            });
            progress.setText("STEP " + (step[0] + 1) + " OF 3");
            back.setVisible(step[0] > 0);
            next.setVisible(step[0] < 2);
            finish.setVisible(step[0] == 2);
        };
        back.addClickListener(event -> {
            step[0]--;
            renderStep.run();
        });
        next.addClickListener(event -> {
            if (step[0] == 0) {
                String canonical = SessionClub.canonicalize(club.getValue(), names);
                if (!canonical.isBlank()) {
                    settings.saveSessionClub(canonical);
                }
            }
            step[0]++;
            renderStep.run();
        });
        renderStep.run();
        dialog.getFooter().add(skip, back, next, finish);
        dialog.open();
    }

    private static Span stepTitle(String number, String title, String copy) {
        Span heading = new Span(number + " · " + title + "\n" + copy);
        heading.addClassName("onboarding-step-title");
        heading.getElement().getStyle().set("white-space", "pre-line");
        return heading;
    }
}
