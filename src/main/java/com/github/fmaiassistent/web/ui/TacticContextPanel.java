package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.tactic.TacticContext;
import com.github.fmaiassistent.tactic.TacticContextService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

final class TacticContextPanel extends Details {
    private static final int MAX_UPLOAD_BYTES = 20 * 1024 * 1024;

    private final TacticContextService contexts;
    private final TextField location = new TextField();
    private final Button load = new Button("Load", VaadinIcon.FOLDER_OPEN.create());
    private final Button clear = new Button("Clear");
    private final Span status = new Span();
    private final Span details = new Span();
    private final Pre preview = new Pre();
    private final Details previewDetails = new Details("Preview AI context", preview);
    private final Upload upload;
    private final Map<String, byte[]> pendingUploads = new LinkedHashMap<>();

    TacticContextPanel(TacticContextService contexts) {
        this.contexts = contexts;
        addClassName("tactic-context-panel");

        location.setPlaceholder("/path/to/tactic.fmf");
        location.setClearButtonVisible(true);
        location.setAriaLabel("Tactic file path");
        location.addClassName("tactic-context-location");
        location.addKeyPressListener(Key.ENTER, ignored -> loadPath());
        load.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        load.addClickListener(ignored -> loadPath());
        clear.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        clear.addClickListener(ignored -> {
            contexts.clear();
            refresh();
        });

        upload = new Upload(UploadHandler.inMemory((metadata, bytes) -> {
            String name = metadata.fileName() == null ? "uploaded-tactic" : metadata.fileName();
            if (bytes.length > MAX_UPLOAD_BYTES) {
                throw new IllegalArgumentException("Tactic upload is larger than 20 MB");
            }
            synchronized (pendingUploads) {
                pendingUploads.put(name, bytes);
            }
        }));
        upload.setUploadButton(new Button("Upload FMF", VaadinIcon.UPLOAD.create()));
        upload.setAcceptedFileTypes(
                ".fmf", ".png", ".jpg", ".jpeg", ".tac", ".aom", ".xml", ".json", ".txt", ".jsb");
        upload.setMaxFileSize(MAX_UPLOAD_BYTES);
        upload.setMaxFiles(10);
        upload.setDropAllowed(false);
        upload.addClassName("tactic-context-upload");
        upload.addAllFinishedListener(ignored -> importUploads());
        upload.addFileRejectedListener(event -> UiFeedback.error(event.getErrorMessage(), 4000, Notification.Position.MIDDLE));

        Span help = new Span(
                "Select or upload an FM26 .fmf tactic. The app decodes its tactical roles, "
                        + "duties, mentality and style directly; screenshots are not required.");
        help.addClassName("tactic-context-help");
        status.addClassName("tactic-context-status");
        details.addClassName("tactic-context-details");
        preview.addClassName("tactic-context-preview");
        previewDetails.addClassName("tactic-context-preview-details");

        HorizontalLayout controls = new HorizontalLayout(location, load, upload, clear);
        controls.setAlignItems(HorizontalLayout.Alignment.END);
        controls.expand(location);
        controls.setWidthFull();
        controls.addClassName("tactic-context-controls");

        VerticalLayout body = new VerticalLayout(help, controls, status, details, previewDetails);
        body.setPadding(false);
        body.setSpacing(true);
        body.addClassName("tactic-context-body");
        add(body);
        refresh();
    }

    private void loadPath() {
        String path = location.getValue();
        runImport(() -> contexts.loadPath(path));
    }

    private void importUploads() {
        Map<String, byte[]> files;
        synchronized (pendingUploads) {
            files = new LinkedHashMap<>(pendingUploads);
            pendingUploads.clear();
        }
        upload.clearFileList();
        if (!files.isEmpty()) {
            runImport(() -> contexts.loadUploads(files));
        }
    }

    private void runImport(Supplier<TacticContext> operation) {
        UI ui = getUI().orElse(null);
        if (ui == null) {
            return;
        }
        setBusy(true);
        UiAsync.submit(
                ui,
                operation,
                context -> {
                    setBusy(false);
                    refresh(context);
                },
                error -> {
                    setBusy(false);
                    UiFeedback.error(safeMessage(error), 4000, Notification.Position.MIDDLE);
                    refresh();
                });
    }

    private void setBusy(boolean busy) {
        load.setEnabled(!busy);
        upload.setEnabled(!busy);
        clear.setEnabled(!busy);
        location.setEnabled(!busy);
        if (busy) {
            status.setText("Reading tactic…");
        }
    }

    private void refresh() {
        refresh(contexts.current());
    }

    void refreshCurrent() {
        refresh();
    }

    void setImportBusy(boolean busy) {
        setBusy(busy);
    }

    private void refresh(TacticContext context) {
        if (!context.active()) {
            setSummaryText("Tactic context · none");
            status.setText("No tactic context is sent with chat messages.");
            details.setText("");
            preview.setText("");
            previewDetails.setVisible(false);
            clear.setVisible(false);
            return;
        }
        setSummaryText("Tactic context · " + context.title());
        status.setText("Active for in-app chat");
        String imported = "Files: " + String.join(", ", context.importedFiles());
        if (!context.warnings().isEmpty()) {
            imported += "\nNotes: " + String.join(" · ", context.warnings());
        }
        details.setText(imported);
        preview.setText(context.markdown());
        previewDetails.setVisible(true);
        clear.setVisible(true);
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? "Could not import tactic" : message;
    }
}
