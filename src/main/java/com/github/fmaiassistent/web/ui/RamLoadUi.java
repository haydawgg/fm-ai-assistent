package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.service.DatabaseLoadAllService;
import com.github.fmaiassistent.service.LoadProgress;
import com.github.fmaiassistent.service.RamLoadCoordinator;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.ModalityMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class RamLoadUi {
    private static final Logger LOGGER = LoggerFactory.getLogger(RamLoadUi.class);

    private RamLoadUi() {
    }

    static void start(RamLoadCoordinator ramLoad, Button loadButton) {
        UI ui = UI.getCurrent();
        Dialog loadingDialog = new Dialog();
        ProgressBar spinner = new ProgressBar();
        Span loadingTitle = new Span("Loading");
        Span loadingSubtitle = new Span();
        spinner.setMin(0);
        spinner.setMax(1);
        spinner.setValue(0);
        spinner.addClassName("loading-progress");
        loadingDialog.setModality(ModalityMode.STRICT);
        loadingDialog.setCloseOnEsc(false);
        loadingDialog.setCloseOnOutsideClick(false);
        loadingTitle.addClassName("loading-text");
        loadingSubtitle.addClassName("loading-text");
        VerticalLayout content = new VerticalLayout(
                new Div(VaadinIcon.DATABASE.create()),
                spinner,
                loadingTitle,
                loadingSubtitle);
        content.setAlignItems(FlexComponent.Alignment.CENTER);
        content.setPadding(true);
        content.addClassName("loading-content");
        loadingDialog.add(content);
        loadingDialog.addClassName("loading-dialog");
        loadingDialog.getElement().getThemeList().add("professional-dialog");

        loadButton.setEnabled(false);
        loadButton.setText("Loading...");
        loadingDialog.open();

        CompletableFuture<DatabaseLoadAllService.LoadAllResult> future =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return ramLoad.loadFromRam(progress -> access(ui, () -> apply(spinner, loadingTitle, loadingSubtitle, progress)));
                    } catch (IOException ex) {
                        throw new CompletionException(ex);
                    }
                });
        ui.addDetachListener(event -> future.cancel(true));
        future
                .thenAccept(result -> access(ui, () -> {
                    loadingDialog.close();
                    restore(loadButton);
                    Notification loaded = Notification.show(
                            "Loaded " + result.players() + " players · in-game "
                                    + (result.gameDate() == null || result.gameDate().isBlank() ? "date unknown" : result.gameDate())
                                    + (result.skipSummary() == null ? "" : result.skipSummary()),
                            3500,
                            Notification.Position.TOP_CENTER);
                    loaded.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    loaded.addClassName("app-toast");
                    ui.getPage().reload();
                }))
                .exceptionally(ex -> {
                    if (future.isCancelled()) {
                        return null;
                    }
                    access(ui, () -> {
                        loadingDialog.close();
                        restore(loadButton);
                        Throwable cause = unwrap(ex);
                        LOGGER.error("Load from RAM failed", cause);
                        Notification failed = Notification.show(
                                "Load failed: " + message(cause),
                                8000,
                                Notification.Position.TOP_CENTER);
                        failed.addThemeVariants(NotificationVariant.LUMO_ERROR);
                        failed.addClassName("app-toast");
                    });
                    return null;
                });
    }

    private static void apply(ProgressBar spinner, Span title, Span subtitle, LoadProgress progress) {
        spinner.setIndeterminate(progress.total() <= 0);
        spinner.setValue(progress.overallFraction());
        title.setText(progress.title());
        subtitle.setText(progress.subtitle());
    }

    private static void restore(Button loadButton) {
        loadButton.setEnabled(true);
        loadButton.setText("Load");
    }

    private static void access(UI ui, Runnable action) {
        if (ui == null || !ui.isAttached()) {
            return;
        }
        try {
            ui.access(action::run);
        } catch (UIDetachedException ignored) {
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current
                && current instanceof CompletionException) {
            current = current.getCause();
        }
        return current;
    }

    private static String message(Throwable cause) {
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName()
                : cause.getMessage();
    }
}
