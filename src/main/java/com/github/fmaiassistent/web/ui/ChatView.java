package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.service.AppSettingsService;
import com.github.fmaiassistent.service.AssistantChatService;
import com.github.fmaiassistent.service.OpenRouterModelCatalog;
import com.github.fmaiassistent.service.PlayerDatabaseService;
import com.github.fmaiassistent.tactic.TacticContextService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.Command;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Route(value = "chat", layout = AppShell.class)
@PageTitle("Chat")
@CssImport("./styles/chat-view.css")
@CssImport(value = "./styles/chat-messages.css", themeFor = "vaadin-message")
public class ChatView extends VerticalLayout {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final long STREAM_PAINT_NANOS = 32_000_000L;
    private static final List<String> STARTERS = List.of(
            "Build my best XI from the live formation",
            "Find affordable wonderkids for my club",
            "Who should I sell or loan out?",
            "Compare my squad with another named club");

    private final AssistantChatService chat;
    private final AppSettingsService settings;
    private final OpenRouterModelCatalog catalog;
    private final PlayerDatabaseService players;

    private final Div transcript = new Div();
    private final TextArea input = new TextArea();
    private final Button send = new Button("Send", VaadinIcon.PAPERPLANE.create());
    private final Button stop = new Button("Stop", VaadinIcon.STOP.create());
    private final Button clear = new Button("New chat", VaadinIcon.PLUS.create());
    private final Button settingsButton = new Button("Settings", VaadinIcon.COG.create());
    private final ComboBox<String> model = OpenRouterModelPicker.comboBox();
    private final Map<String, String> modelLabels = new LinkedHashMap<>();
    private final Span snapshot = new Span();
    private final Div welcome = new Div();
    private final Div unconfigured = new Div();
    private final Div starters = new Div();

    private Disposable activeStream;
    private AssistantTurn activeTurn;
    private boolean applyingModel;
    private final List<AssistantChatService.ChatTurn> history = new ArrayList<>();

    public ChatView(
            AssistantChatService chat,
            AppSettingsService settings,
            OpenRouterModelCatalog catalog,
            PlayerDatabaseService players,
            TacticContextService tacticContexts) {
        this.chat = chat;
        this.settings = settings;
        this.catalog = catalog;
        this.players = players;

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("chat-view");

        transcript.addClassName("chat-transcript");
        transcript.setWidthFull();
        transcript.getElement().setAttribute("aria-live", "polite");

        buildWelcome();
        buildUnconfigured();
        transcript.add(unconfigured, welcome);

        add(toolbar(), new TacticContextPanel(tacticContexts), transcript, composer());
        setFlexGrow(1, transcript);
        OpenRouterModelPicker.bind(model, catalog, settings.openRouterModel(), true, modelLabels);
        refreshSnapshot();
        updateConfigurationState();
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        refreshSnapshot();
        updateConfigurationState();
    }

    @Override
    protected void onDetach(DetachEvent event) {
        stopStream();
        super.onDetach(event);
    }

    private Component toolbar() {
        Span title = new Span("FM AI chat");
        title.addClassName("chat-title");
        snapshot.addClassName("chat-snapshot");

        model.setLabel("");
        model.setPlaceholder("Model");
        model.setAriaLabel("Model");
        model.setWidth("18rem");
        model.addClassName("chat-model");
        model.addValueChangeListener(event -> {
            if (!event.isFromClient() || applyingModel) {
                return;
            }
            String selected = event.getValue();
            if (selected == null || selected.isBlank()) {
                return;
            }
            settings.saveOpenRouter(settings.openRouterApiKey(), selected);
        });

        clear.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        clear.addClickListener(event -> clearChat());
        settingsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        settingsButton.addClickListener(event -> SettingsDialog.open(
                settings, catalog, settings.currency(), ignored -> {
                    applyingModel = true;
                    try {
                        OpenRouterModelPicker.apply(model, modelLabels, catalog.cachedModels(), settings.openRouterModel());
                    } finally {
                        applyingModel = false;
                    }
                    updateConfigurationState();
                }));

        HorizontalLayout identity = new HorizontalLayout(title, snapshot);
        identity.setAlignItems(FlexComponent.Alignment.CENTER);
        identity.setSpacing(true);
        identity.addClassName("chat-identity");

        HorizontalLayout actions = new HorizontalLayout(model, clear, settingsButton);
        actions.setAlignItems(FlexComponent.Alignment.CENTER);
        actions.setSpacing(true);
        actions.addClassName("chat-toolbar-actions");

        HorizontalLayout header = new HorizontalLayout(identity, actions);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.addClassName("chat-toolbar");
        return header;
    }

    private void buildWelcome() {
        welcome.addClassName("chat-welcome");
        H3 title = new H3("What are we solving today?");
        Span copy = new Span("Ask naturally. I inspect the loaded FM snapshot with the same recruitment and squad tools as /mcp. Upload an .fmf below for role-fit context.");
        copy.addClassName("chat-welcome-copy");
        Span tip = new Span("Every answer is grounded in your latest saved snapshot. Mention a budget, position, or club to make recommendations sharper.");
        tip.addClassName("chat-welcome-tip");
        starters.addClassName("chat-starters");
        for (String prompt : STARTERS) {
            Button button = new Button(prompt, event -> {
                if (!chat.configured() || activeStream != null) {
                    return;
                }
                input.setValue(prompt);
                send();
            });
            button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            button.addClassName("chat-starter");
            starters.add(button);
        }
        welcome.add(title, copy, tip, starters);
    }

    private void buildUnconfigured() {
        unconfigured.addClassName("chat-unconfigured");
        H3 title = new H3("Add an OpenRouter key");
        Span copy = new Span("In-app chat needs an OpenRouter API key. It stays in fm-ai-assistent.properties on this machine.");
        copy.addClassName("chat-welcome-copy");
        Button openSettings = new Button("Open Settings", VaadinIcon.COG.create(), event -> SettingsDialog.open(
                settings, catalog, settings.currency(), ignored -> {
                    applyingModel = true;
                    try {
                        OpenRouterModelPicker.apply(model, modelLabels, catalog.cachedModels(), settings.openRouterModel());
                    } finally {
                        applyingModel = false;
                    }
                    updateConfigurationState();
                }));
        openSettings.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        unconfigured.add(title, copy, openSettings);
    }

    private Component composer() {
        input.setPlaceholder("Ask about your squad, transfers, tactics, or a player...");
        input.setWidthFull();
        input.setMinHeight("4.5em");
        input.setMaxHeight("12em");
        input.setAriaLabel("Message");
        input.getElement().addEventListener("keydown", event -> send())
                .setFilter("event.key === 'Enter' && !event.shiftKey")
                .addEventData("event.preventDefault()");

        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        send.addClickListener(event -> send());
        send.addClassName("chat-send");
        send.getElement().setAttribute("aria-label", "Send message");
        stop.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        stop.setVisible(false);
        stop.addClickListener(event -> stopStream());
        stop.addClassName("chat-stop");
        stop.getElement().setAttribute("aria-label", "Stop generating");
        clear.getElement().setAttribute("aria-label", "Start a new chat");

        Span hint = new Span("Enter to send · Shift + Enter for a new line");
        hint.addClassName("chat-composer-hint");

        HorizontalLayout actions = new HorizontalLayout(send, stop);
        actions.setPadding(false);
        actions.setSpacing(true);
        actions.setAlignItems(FlexComponent.Alignment.END);

        HorizontalLayout row = new HorizontalLayout(input, actions);
        row.setWidthFull();
        row.setPadding(false);
        row.setAlignItems(FlexComponent.Alignment.END);
        row.setFlexGrow(1, input);
        row.addClassName("chat-composer-row");

        VerticalLayout composer = new VerticalLayout(row, hint);
        composer.setPadding(false);
        composer.setSpacing(false);
        composer.setWidthFull();
        composer.addClassName("chat-composer");
        return composer;
    }

    private void refreshSnapshot() {
        long count = players.countPlayers();
        snapshot.setText(count <= 0
                ? "No RAM snapshot — load from Desk"
                : count + " players loaded");
        snapshot.getElement().setAttribute("data-empty", count <= 0);
    }

    private void updateConfigurationState() {
        boolean configured = chat.configured();
        boolean streaming = activeStream != null;
        input.setEnabled(configured && !streaming);
        send.setEnabled(configured && !streaming);
        send.setVisible(!streaming);
        stop.setVisible(streaming);
        stop.setEnabled(streaming);
        stop.getElement().setAttribute("data-busy", streaming);
        model.setEnabled(!streaming);
        clear.setEnabled(!streaming);
        starters.getChildren().forEach(child -> {
            if (child instanceof Button button) {
                button.setEnabled(configured && !streaming);
            }
        });
        unconfigured.setVisible(!configured);
        welcome.setVisible(configured && !hasMessages());
    }

    private boolean hasMessages() {
        return transcript.getChildren().anyMatch(child -> child.getElement().getClassList().contains("chat-message"));
    }

    private void send() {
        String message = input.getValue();
        if (message == null || message.isBlank() || activeStream != null || !chat.configured()) {
            return;
        }
        appendUserMessage(message.trim());
        input.clear();
        welcome.setVisible(false);
        unconfigured.setVisible(false);

        AssistantTurn turn = new AssistantTurn();
        activeTurn = turn;
        transcript.add(turn.root);
        scrollToLatest();
        updateConfigurationState();

        UI ui = getUI().orElse(null);
        StringBuilder response = new StringBuilder();
        AtomicBoolean first = new AtomicBoolean(true);
        AtomicLong lastPaint = new AtomicLong(0);
        List<AssistantChatService.ChatTurn> prior = List.copyOf(history);
        history.add(new AssistantChatService.ChatTurn(true, message.trim()));
        activeStream = chat.stream(prior, message.trim())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        token -> access(ui, () -> {
                            response.append(token);
                            turn.buffer(response.toString());
                            if (first.compareAndSet(true, false)) {
                                turn.showContent();
                            }
                            long now = System.nanoTime();
                            if (now - lastPaint.get() >= STREAM_PAINT_NANOS) {
                                lastPaint.set(now);
                                turn.paint();
                                scrollToLatest();
                            }
                        }),
                        error -> access(ui, () -> {
                            if (!turn.close()) {
                                return;
                            }
                            if (first.get()) {
                                turn.showContent();
                            }
                            rememberAssistant(response.toString());
                            turn.setError("I couldn't complete that request. " + safeMessage(error));
                            turn.finishStreaming();
                            finishStream();
                        }),
                        () -> access(ui, () -> {
                            if (!turn.close()) {
                                return;
                            }
                            if (first.get()) {
                                turn.showContent();
                            }
                            turn.setMarkdown(response.toString());
                            rememberAssistant(response.toString());
                            turn.finishStreaming();
                            finishStream();
                            scrollToLatest();
                        }));
    }

    private void appendUserMessage(String text) {
        Div message = new Div();
        message.addClassName("chat-message");
        message.addClassName("chat-message-user");
        Span meta = new Span("You · " + LocalTime.now().format(TIME_FORMAT));
        meta.addClassName("chat-message-meta");
        Span body = new Span(text);
        body.addClassName("chat-message-body");
        message.add(meta, body);
        transcript.add(message);
    }

    private void clearChat() {
        stopStream();
        history.clear();
        transcript.removeAll();
        transcript.add(unconfigured, welcome);
        updateConfigurationState();
    }

    private void rememberAssistant(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        history.add(new AssistantChatService.ChatTurn(false, text));
    }

    private void stopStream() {
        AssistantTurn turn = activeTurn;
        if (activeStream != null) {
            activeStream.dispose();
            activeStream = null;
        }
        if (turn != null && turn.close()) {
            if (!turn.hasContent()) {
                turn.showContent();
                turn.setMarkdown("Stopped.");
            } else {
                turn.paint();
                rememberAssistant(turn.rawText());
            }
            turn.finishStreaming();
        }
        finishStream();
    }

    private void finishStream() {
        activeStream = null;
        activeTurn = null;
        updateConfigurationState();
    }

    private void scrollToLatest() {
        transcript.getElement().executeJs("this.scrollTop = this.scrollHeight");
    }

    private void access(UI ui, Command action) {
        if (ui == null || !ui.isAttached()) {
            return;
        }
        try {
            ui.access(action);
        } catch (UIDetachedException ignored) {
        }
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank() ? "Please try again." : error.getMessage();
    }

    private static String sanitizeMarkdown(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        return markdown
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replaceAll("(?i)]\\s*\\((?:javascript|data|vbscript):", "](#blocked-");
    }

    private static final class AssistantTurn {
        private final Div root = new Div();
        private final Div typing = new Div();
        private final Span typingLabel = new Span("Thinking");
        private final Markdown body = new Markdown("");
        private final Button copy = new Button(VaadinIcon.COPY.create());
        private String raw = "";
        private boolean contentVisible;
        private final AtomicBoolean closed = new AtomicBoolean();

        private AssistantTurn() {
            root.addClassName("chat-message");
            root.addClassName("chat-message-assistant");
            Span meta = new Span("FM AI · " + LocalTime.now().format(TIME_FORMAT));
            meta.addClassName("chat-message-meta");
            copy.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            copy.addClassName("chat-copy");
            copy.getElement().setAttribute("aria-label", "Copy reply");
            copy.setVisible(false);
            copy.addClickListener(event -> {
                if (raw.isBlank()) {
                    return;
                }
                UI ui = UI.getCurrent();
                if (ui != null) {
                    ui.getPage().executeJs("navigator.clipboard.writeText($0)", raw);
                }
                Notification.show("Copied", 1200, Notification.Position.BOTTOM_CENTER)
                        .addClassName("app-toast");
            });
            HorizontalLayout heading = new HorizontalLayout(meta, copy);
            heading.setWidthFull();
            heading.setAlignItems(FlexComponent.Alignment.CENTER);
            heading.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
            heading.addClassName("chat-message-heading");

            typing.addClassName("chat-typing");
            typingLabel.addClassName("chat-typing-label");
            Div dots = new Div(dot(), dot(), dot());
            dots.addClassName("chat-typing-dots");
            typing.add(dots, typingLabel);
            typing.getElement().setAttribute("aria-live", "polite");
            typing.getElement().setAttribute("aria-label", "Thinking");

            body.addClassName("chat-markdown");
            body.addClassName("chat-streaming");
            body.setVisible(false);
            root.add(heading, typing, body);
        }

        private static Span dot() {
            Span dot = new Span();
            dot.addClassName("chat-typing-dot");
            return dot;
        }

        private boolean close() {
            return closed.compareAndSet(false, true);
        }

        private void showContent() {
            contentVisible = true;
            typing.addClassName("chat-typing-compact");
            typingLabel.setText("Writing");
            typing.getElement().setAttribute("aria-label", "Writing");
            body.setVisible(true);
            copy.setVisible(true);
        }

        private void finishStreaming() {
            typing.setVisible(false);
            body.removeClassName("chat-streaming");
        }

        private boolean hasContent() {
            return contentVisible && !raw.isBlank();
        }

        private String rawText() {
            return raw;
        }

        private void setMarkdown(String markdown) {
            buffer(markdown);
            paint();
        }

        private void buffer(String markdown) {
            raw = markdown == null ? "" : markdown;
        }

        private void paint() {
            body.setContent(sanitizeMarkdown(raw));
        }

        private void setError(String message) {
            showContent();
            raw = message == null ? "" : message;
            body.setContent(sanitizeMarkdown(raw));
            body.addClassName("chat-error");
            finishStreaming();
        }
    }
}
