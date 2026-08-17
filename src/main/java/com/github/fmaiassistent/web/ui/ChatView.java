package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.ChatMessageEntity;
import com.github.fmaiassistent.domain.entity.ChatSessionEntity;
import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.linux.GameDateFinder;
import com.github.fmaiassistent.mcp.FmAiAssistentTools;
import com.github.fmaiassistent.repository.PlayerFilterCriteria;
import com.github.fmaiassistent.service.AppSettingsService;
import com.github.fmaiassistent.service.AssistantChatService;
import com.github.fmaiassistent.service.ChatTone;
import com.github.fmaiassistent.service.ChatSessionService;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.github.fmaiassistent.service.OpenRouterModelCatalog;
import com.github.fmaiassistent.service.PlayerDatabaseService;
import com.github.fmaiassistent.tactic.TacticContextService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.streams.UploadHandler;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Route(value = "chat", layout = AppShell.class)
@PageTitle("Chat")
@CssImport("./styles/chat-view.css")
@CssImport("./styles/highlight-github-dark.css")
@CssImport(value = "./styles/chat-messages.css", themeFor = "vaadin-message")
@JavaScript("./js/highlight.min.js")
public class ChatView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("d MMM");
    private static final long STREAM_PAINT_NANOS = 32_000_000L;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> STARTERS = List.of(
            "Build my best XI from the live formation",
            "Find affordable wonderkids for my club",
            "Who should I sell or loan out?",
            "Compare my squad with another named club");

    private final AssistantChatService chat;
    private final AppSettingsService settings;
    private final OpenRouterModelCatalog catalog;
    private final PlayerDatabaseService players;
    private final ClubDatabaseService clubs;
    private final ChatSessionService sessions;
    private final TacticContextService tacticContexts;
    private final FmAiAssistentTools tools;
    private final TacticContextPanel tacticPanel;

    private final Div sessionList = new Div();
    private final Div transcript = new Div();
    private final TextArea input = new TextArea();
    private final Button send = new Button("Send", VaadinIcon.PAPERPLANE.create());
    private final Button stop = new Button("Stop", VaadinIcon.STOP.create());
    private final Button clear = new Button("New chat", VaadinIcon.PLUS.create());
    private final Button export = new Button("Export", VaadinIcon.DOWNLOAD.create());
    private final Button settingsButton = new Button("Settings", VaadinIcon.COG.create());
    private final ComboBox<String> model = OpenRouterModelPicker.comboBox();
    private final Map<String, String> modelLabels = new LinkedHashMap<>();
    private final Span snapshot = new Span();
    private final Span sessionCost = new Span();
    private final Span omitted = new Span();
    private final Span staleBanner = new Span();
    private final Span composerHint = new Span();
    private final Div welcome = new Div();
    private final Div unconfigured = new Div();
    private final Div starters = new Div();
    private final ComboBox<String> mention = new ComboBox<>();
    private final ComboBox<String> slash = new ComboBox<>();
    private final TextField sessionSearch = new TextField();
    private final Select<ChatTone> tone = new Select<>();
    private final Button pinModel = new Button(VaadinIcon.STAR.create());
    private final Component sidebar;

    private Disposable activeStream;
    private AssistantTurn activeTurn;
    private boolean applyingModel;
    private String conversationId;
    private final List<AssistantChatService.ChatTurn> history = new ArrayList<>();
    private String pendingPrompt = "";
    private String lastUserText = "";
    private final Deque<String> queuedMessages = new ArrayDeque<>();
    private final LinkedHashSet<String> triedModels = new LinkedHashSet<>();
    private String pendingFallbackModel = "";
    private String currentModel = "";
    private int lastUserOrdinal = -1;
    private double sessionCostUsd;
    private Integer pendingReplaceFrom;
    private List<String> cachedSquadNames = List.of();
    private List<String> cachedClubNames = List.of();

    public ChatView(
            AssistantChatService chat,
            AppSettingsService settings,
            OpenRouterModelCatalog catalog,
            PlayerDatabaseService players,
            ClubDatabaseService clubs,
            ChatSessionService sessions,
            TacticContextService tacticContexts,
            FmAiAssistentTools tools) {
        this.chat = chat;
        this.settings = settings;
        this.catalog = catalog;
        this.players = players;
        this.clubs = clubs;
        this.sessions = sessions;
        this.tacticContexts = tacticContexts;
        this.tools = tools;
        this.tacticPanel = new TacticContextPanel(tacticContexts);
        this.sidebar = buildSidebar();

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("chat-view");

        transcript.addClassName("chat-transcript");
        transcript.setWidthFull();
        transcript.getElement().setAttribute("role", "log");
        sessionList.addClassName("chat-session-list");

        buildWelcome();
        buildUnconfigured();
        transcript.add(unconfigured, welcome);

        VerticalLayout main = new VerticalLayout(toolbar(), staleBanner, omitted, tacticPanel, transcript, composer());
        main.setPadding(false);
        main.setSpacing(false);
        main.setSizeFull();
        main.addClassName("chat-main");
        main.setFlexGrow(1, transcript);

        HorizontalLayout body = new HorizontalLayout(sidebar, main);
        body.setSizeFull();
        body.setPadding(false);
        body.setSpacing(false);
        body.setFlexGrow(0, sidebar);
        body.setFlexGrow(1, main);
        body.addClassName("chat-body");
        add(body);
        setFlexGrow(1, body);

        OpenRouterModelPicker.bind(model, catalog, settings.openRouterModel(), true, modelLabels, settings.pinnedModels());
        refreshPinnedButton();
        refreshSnapshot();
        updateConfigurationState();
        Shortcuts.addShortcutListener(this, this::openCommandPalette, Key.KEY_K)
                .withCtrl()
                .listenOn(this);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        pendingPrompt = event.getLocation().getQueryParameters().getSingleParameter("q").orElse("");
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        refreshCachedNames();
        openOrRestoreSession();
        refreshSnapshot();
        updateConfigurationState();
        refreshSessions();
        getElement().executeJs("""
                this.addEventListener('paste', event => {
                  const items = event.clipboardData ? event.clipboardData.items : [];
                  for (const item of items) {
                    if (item.type && item.type.startsWith('image/')) {
                      event.preventDefault();
                      const file = item.getAsFile();
                      const reader = new FileReader();
                      reader.onload = () => $0.$server.receivePastedImage(file.name || 'pasted.png', reader.result);
                      reader.readAsDataURL(file);
                      return;
                    }
                  }
                });
                """, getElement());
        if (!pendingPrompt.isBlank() && chat.configured()) {
            submitPendingPrompt(event.getUI());
        } else if (input.getValue() == null || input.getValue().isBlank()) {
            String draft = ChatUiContext.draft();
            if (!draft.isBlank()) {
                input.setValue(draft);
            }
        }
    }

    @Override
    protected void onDetach(DetachEvent event) {
        ChatUiContext.setDraft(input.getValue());
        stopStream(false);
        super.onDetach(event);
    }

    private Component buildSidebar() {
        Span heading = new Span("Chats");
        heading.addClassName("chat-session-heading");
        Button neu = new Button("New", VaadinIcon.PLUS.create(), event -> confirmNewChat());
        neu.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        HorizontalLayout header = new HorizontalLayout(heading, neu);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        sessionSearch.setPlaceholder("Search chats");
        sessionSearch.setWidthFull();
        sessionSearch.setClearButtonVisible(true);
        sessionSearch.setValueChangeMode(ValueChangeMode.EAGER);
        sessionSearch.addValueChangeListener(event -> refreshSessions());
        VerticalLayout sidebar = new VerticalLayout(header, sessionSearch, sessionList);
        sidebar.setPadding(true);
        sidebar.setSpacing(false);
        sidebar.setWidth("16rem");
        sidebar.addClassName("chat-sidebar");
        return sidebar;
    }

    private Component toolbar() {
        Span title = new Span("FM AI chat");
        title.addClassName("chat-title");
        snapshot.addClassName("chat-snapshot");
        sessionCost.addClassName("chat-session-cost");

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
            refreshPinnedButton();
        });

        pinModel.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        pinModel.getElement().setAttribute("aria-label", "Pin model");
        pinModel.addClickListener(event -> {
            if (model.getValue() != null && !model.getValue().isBlank()) {
                settings.togglePinnedModel(model.getValue());
                OpenRouterModelPicker.apply(model, modelLabels, catalog.cachedModels(), model.getValue(), settings.pinnedModels());
                refreshPinnedButton();
            }
        });

        tone.setLabel("");
        tone.setAriaLabel("Chat tone");
        tone.setItems(ChatTone.values());
        tone.setItemLabelGenerator(ChatTone::label);
        tone.setValue(settings.chatTone());
        tone.setWidth("9rem");
        tone.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                settings.saveChatTone(event.getValue());
            }
        });

        clear.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        clear.addClickListener(event -> confirmNewChat());
        export.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        export.addClickListener(event -> exportMarkdown());
        settingsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        settingsButton.addClickListener(event -> SettingsDialog.open(
                settings, catalog, settings.currency(), ignored -> {
                    applyingModel = true;
                    try {
                        OpenRouterModelPicker.apply(model, modelLabels, catalog.cachedModels(), settings.openRouterModel(), settings.pinnedModels());
                    } finally {
                        applyingModel = false;
                     }
                     updateConfigurationState();
                     refreshStarters();
                     submitPendingPrompt(UI.getCurrent());
                 }));

        HorizontalLayout identity = new HorizontalLayout(title, snapshot, sessionCost);
        identity.setAlignItems(FlexComponent.Alignment.CENTER);
        identity.setSpacing(true);
        identity.addClassName("chat-identity");

        HorizontalLayout actions = new HorizontalLayout(tone, model, pinModel, clear, export, settingsButton);
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
        Span tip = new Span("Type / for commands or @ to mention a squad player. Answers stay grounded in your latest snapshot.");
        tip.addClassName("chat-welcome-tip");
        starters.addClassName("chat-starters");
        refreshStarters();
        welcome.add(title, copy, tip, starters);
    }

    private void refreshStarters() {
        starters.removeAll();
        if (players.countPlayers() <= 0) {
            Button load = new Button("Load from RAM first", event -> Notification.show(
                    "Use Load in the top bar with FM26 running.", 2500, Notification.Position.TOP_CENTER));
            load.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            load.addClassName("chat-starter");
            starters.add(load);
            return;
        }
        LinkedHashSet<String> prompts = new LinkedHashSet<>(STARTERS);
        for (SavedChatPrompt prompt : settings.chatPrompts()) {
            prompts.add(prompt.text());
        }
        for (String prompt : prompts) {
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
                        OpenRouterModelPicker.apply(model, modelLabels, catalog.cachedModels(), settings.openRouterModel(), settings.pinnedModels());
                    } finally {
                        applyingModel = false;
                    }
                     updateConfigurationState();
                     refreshStarters();
                     submitPendingPrompt(UI.getCurrent());
                 }));
        openSettings.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        unconfigured.add(title, copy, openSettings);
    }

    private Component composer() {
        omitted.addClassName("chat-omitted");
        omitted.setVisible(false);
        staleBanner.addClassName("chat-stale");
        staleBanner.setVisible(false);

        input.setPlaceholder("Ask about your squad, transfers, tactics, or a player...  /xi  @name");
        input.setWidthFull();
        input.setMinHeight("4.5em");
        input.setMaxHeight("12em");
        input.setAriaLabel("Message");
        input.setValueChangeMode(ValueChangeMode.EAGER);
        input.addValueChangeListener(event -> {
            updateComposerHint();
            maybeOfferMention(event.getValue());
            maybeOfferSlash(event.getValue());
            ChatUiContext.setDraft(event.getValue());
        });
        input.getElement().addEventListener("keydown", event -> send())
                .setFilter("event.key === 'Enter' && !event.shiftKey")
                .addEventData("event.preventDefault()");
        input.getElement().addEventListener("keydown", event -> editLastUser())
                .setFilter("event.key === 'ArrowUp' && event.target.value === ''");

        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        send.addClickListener(event -> send());
        send.addClassName("chat-send");
        send.getElement().setAttribute("aria-label", "Send message");
        send.getElement().setProperty("title", chat.configured() ? "Send" : "Set an OpenRouter key in Settings to use in-app chat");
        stop.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        stop.setVisible(false);
        stop.addClickListener(event -> stopStream());
        stop.addClassName("chat-stop");
        stop.getElement().setAttribute("aria-label", "Stop generating");
        clear.getElement().setAttribute("aria-label", "Start a new chat");

        composerHint.addClassName("chat-composer-hint");
        updateComposerHint();

        mention.setPlaceholder("@ player");
        mention.setClearButtonVisible(true);
        mention.setVisible(false);
        mention.addValueChangeListener(event -> {
            if (event.getValue() == null || event.getValue().isBlank()) {
                return;
            }
            String current = input.getValue() == null ? "" : input.getValue();
            int at = current.lastIndexOf('@');
            String prefix = at >= 0 ? current.substring(0, at) : current;
            input.setValue(prefix + event.getValue() + " ");
            mention.clear();
            mention.setVisible(false);
            input.focus();
        });
        slash.setPlaceholder("/ command");
        slash.setClearButtonVisible(true);
        slash.setVisible(false);
        slash.addValueChangeListener(event -> {
            if (event.getValue() == null || event.getValue().isBlank()) {
                return;
            }
            String name = event.getValue().split(" — ", 2)[0];
            ChatSlashCommands.COMMANDS.stream()
                    .filter(command -> command.name().equals(name))
                    .findFirst()
                    .ifPresent(command -> input.setValue(command.prompt()));
            slash.clear();
            slash.setVisible(false);
            input.focus();
        });

        Map<String, byte[]> pendingDrop = new ConcurrentHashMap<>();
        Upload drop = new Upload(UploadHandler.inMemory((metadata, bytes) -> {
            String name = metadata.fileName() == null ? "uploaded-tactic" : metadata.fileName();
            pendingDrop.put(name, bytes);
        }));
        drop.setDropAllowed(true);
        drop.setAutoUpload(true);
        drop.setMaxFiles(4);
        drop.setMaxFileSize(20 * 1024 * 1024);
        drop.setAcceptedFileTypes(".fmf", ".png", ".jpg", ".jpeg");
        drop.setUploadButton(new Button("Drop .fmf", VaadinIcon.UPLOAD.create()));
        drop.addClassName("chat-drop");
        drop.addAllFinishedListener(event -> {
            Map<String, byte[]> files = new LinkedHashMap<>(pendingDrop);
            pendingDrop.clear();
            importChatTacticFiles(files, "Tactic context updated");
        });

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

        VerticalLayout composer = new VerticalLayout(row, mention, slash, drop, composerHint);
        composer.setPadding(false);
        composer.setSpacing(false);
        composer.setWidthFull();
        composer.addClassName("chat-composer");
        return composer;
    }

    private void refreshSnapshot() {
        SnapshotHeartbeat.Status status = SnapshotHeartbeat.from(players.metadata(), players.countPlayers());
        snapshot.setText(status.label());
        snapshot.getElement().setAttribute("data-empty", status.empty());
        snapshot.getElement().setAttribute("data-stale", status.stale());
        staleBanner.setVisible(status.empty() || status.stale());
        staleBanner.setText(status.empty()
                ? "No RAM snapshot loaded — answers will be thin until you load from the top bar."
                : "Snapshot is stale. Load from RAM again if the save has moved on.");
    }

    private void updateComposerHint() {
        String text = input.getValue() == null ? "" : input.getValue();
        int approx = catalog.estimatePromptTokens(text);
        Double estimate = catalog.estimateUsd(settings.openRouterModel(), approx, 600);
        double cap = settings.dailySpendCapUsd();
        double spent = todaySpendUsd();
        StringBuilder hint = new StringBuilder("Enter to send · Shift+Enter newline · ~" + approx + " tok");
        if (estimate != null) {
            hint.append(String.format(" · est. $%.4f", estimate));
        }
        if (cap > 0) {
            hint.append(String.format(" · cap $%.2f ($%.4f today)", cap, spent));
        }
        if (activeStream != null) {
            hint.append(" · sending queues until this reply finishes");
            if (!queuedMessages.isEmpty()) {
                hint.append(" (" + queuedMessages.size() + " queued)");
            }
        }
        composerHint.setText(hint.toString());
    }

    private void updateOmitted() {
        int omittedCount = AssistantChatService.omittedCount(history);
        omitted.setVisible(omittedCount > 0);
        omitted.setText(omittedCount + " earlier messages are omitted from the model context.");
    }

    private void updateConfigurationState() {
        boolean configured = chat.configured();
        boolean streaming = activeStream != null;
        input.setEnabled(configured);
        send.setEnabled(configured);
        send.setVisible(true);
        send.getElement().setProperty("title", configured
                ? "Send"
                : "Set an OpenRouter key in Settings to use in-app chat");
        stop.setVisible(streaming);
        stop.setEnabled(streaming);
        stop.getElement().setAttribute("data-busy", streaming);
        model.setEnabled(true);
        clear.setEnabled(true);
        export.setEnabled(hasMessages());
        starters.getChildren().forEach(child -> {
            if (child instanceof Button button) {
                button.setEnabled(configured);
            }
        });
        unconfigured.setVisible(!configured);
        welcome.setVisible(configured && !hasMessages());
        updateOmitted();
    }

    private boolean hasMessages() {
        return transcript.getChildren().anyMatch(child -> child.getElement().getClassList().contains("chat-message"));
    }

    private void send() {
        String raw = input.getValue();
        if (raw == null || raw.isBlank() || !chat.configured()) {
            return;
        }
        String message = ChatSlashCommands.expand(raw.trim(), sessionClubName()).orElse(raw.trim());
        if (activeStream != null) {
            queuedMessages.add(message);
            input.clear();
            ChatUiContext.setDraft("");
            int waiting = queuedMessages.size();
            String note = waiting > 1
                    ? "Queued — " + waiting + " messages waiting"
                    : "Queued — sending when this reply finishes";
            Notification.show(note, 1800, Notification.Position.BOTTOM_CENTER)
                    .addClassName("app-toast");
            updateComposerHint();
            return;
        }
        Double estimate = catalog.estimateUsd(
                settings.openRouterModel(),
                catalog.estimatePromptTokens(message, history.stream().map(AssistantChatService.ChatTurn::text).reduce("", String::concat)),
                600);
        if (dailyCapBlocked(message, estimate)) {
            return;
        }
        input.clear();
        ChatUiContext.setDraft("");
        mention.setVisible(false);
        slash.setVisible(false);
        if (pendingReplaceFrom != null && conversationId != null) {
            sessions.deleteFrom(conversationId, pendingReplaceFrom);
            pendingReplaceFrom = null;
            reloadTranscript();
        }
        streamUserMessage(message, true, null);
    }

    private void streamUserMessage(String message, boolean persistUser) {
        streamUserMessage(message, persistUser, null);
    }

    private void streamUserMessage(String message, boolean persistUser, String modelOverride) {
        ensureSession();
        welcome.setVisible(false);
        unconfigured.setVisible(false);
        lastUserText = message;
        String modelId = modelOverride == null || modelOverride.isBlank()
                ? settings.openRouterModel()
                : modelOverride;
        currentModel = modelId;
        List<AssistantChatService.ChatTurn> prior = historyForModel();
        if (persistUser) {
            ChatMessageEntity saved = persist("user", message, modelId, ChatSessionService.MessageExtras.NONE);
            lastUserOrdinal = saved.getOrdinal();
            transcript.add(userCard(message, lastUserOrdinal));
            history.add(new AssistantChatService.ChatTurn(true, message));
        } else if (!history.isEmpty() && history.getLast().user()) {
            prior = historyForModel(history.subList(0, history.size() - 1));
        }
        refreshSessions();
        updateOmitted();

        if (modelOverride == null || modelOverride.isBlank()) {
            triedModels.clear();
            triedModels.add(settings.openRouterModel());
            pendingFallbackModel = "";
        } else {
            triedModels.add(modelId);
        }
        AssistantTurn turn = new AssistantTurn(modelId, message, lastUserOrdinal);
        activeTurn = turn;
        transcript.add(turn.root);
        scrollToLatest();
        updateConfigurationState();
        updateComposerHint();

        UI ui = getUI().orElse(null);
        StringBuffer response = new StringBuffer();
        StringBuffer reasoningBuffer = new StringBuffer();
        AtomicBoolean first = new AtomicBoolean(true);
        AtomicLong lastPaint = new AtomicLong(0);
        AtomicLong started = new AtomicLong(System.nanoTime());
        AtomicLong firstToken = new AtomicLong(0);
        List<AssistantChatService.ToolTrace> traces = new ArrayList<>();
        AssistantChatService.UsageSnapshot[] usage = {new AssistantChatService.UsageSnapshot(null, null)};
        String[] generationId = {""};
        AssistantChatService.ThinkSplitter splitter = new AssistantChatService.ThinkSplitter();
        activeStream = chat.streamEvents(prior, message, conversationId, grounding(), modelOverride)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        event -> {
                            if (event.kind() == AssistantChatService.ChatStreamEvent.Kind.TOOL_TRACE && event.trace() != null) {
                                traces.add(event.trace());
                            }
                            if (event.kind() == AssistantChatService.ChatStreamEvent.Kind.TOOL) {
                                splitter.reset();
                                synchronized (response) {
                                    response.setLength(0);
                                }
                            }
                            if (event.generationId() != null && !event.generationId().isBlank()) {
                                generationId[0] = event.generationId();
                            }
                            if (event.usage() != null && (event.usage().promptTokens() != null || event.usage().completionTokens() != null)) {
                                usage[0] = event.usage();
                            }
                            String reasoningUpdate = null;
                            boolean hasAnswer = false;
                            if (event.kind() == AssistantChatService.ChatStreamEvent.Kind.REASONING) {
                                reasoningUpdate = event.text();
                            } else if (event.kind() == AssistantChatService.ChatStreamEvent.Kind.TOKEN && event.text() != null && !event.text().isEmpty()) {
                                AssistantChatService.ThinkSplitter.Piece piece = splitter.push(event.text());
                                if (!piece.reasoning().isBlank()) {
                                    reasoningUpdate = piece.reasoning();
                                }
                                if (!piece.answer().isEmpty()) {
                                    hasAnswer = true;
                                    if (firstToken.get() == 0) {
                                        firstToken.set(System.nanoTime());
                                    }
                                    synchronized (response) {
                                        response.append(piece.answer());
                                    }
                                }
                            }
                            if (reasoningUpdate != null && !reasoningUpdate.isBlank()) {
                                synchronized (reasoningBuffer) {
                                    reasoningBuffer.append(reasoningUpdate);
                                }
                            }
                            final String fr = reasoningUpdate;
                            final String eventText = event.text();
                            final AssistantChatService.ToolTrace eventTrace = event.trace();
                            final boolean isTool = event.kind() == AssistantChatService.ChatStreamEvent.Kind.TOOL;
                            final boolean isToolTrace = event.kind() == AssistantChatService.ChatStreamEvent.Kind.TOOL_TRACE;
                            final boolean answered = hasAnswer;
                            access(ui, () -> {
                                if (isTool) {
                                    turn.discardProgressText();
                                    turn.addTool(eventText);
                                    return;
                                }
                                if (isToolTrace && eventTrace != null) {
                                    turn.addTrace(eventTrace);
                                    return;
                                }
                                if (fr != null) {
                                    turn.addReasoning(fr);
                                }
                                if (!answered) {
                                    return;
                                }
                                synchronized (response) {
                                    turn.buffer(response.toString());
                                }
                                if (first.compareAndSet(true, false)) {
                                    turn.showContent();
                                }
                                long now = System.nanoTime();
                                if (now - lastPaint.get() >= STREAM_PAINT_NANOS) {
                                    lastPaint.set(now);
                                    turn.paint();
                                    scrollToLatest();
                                }
                            });
                        },
                        error -> access(ui, () -> {
                            if (!dailyCapBlocked(lastUserText) && tryFallback()) {
                                if (turn.close()) {
                                    turn.root.removeFromParent();
                                }
                                finishStream(false);
                                streamUserMessage(lastUserText, false, pendingFallbackModel);
                                return;
                            }
                            if (!turn.close()) {
                                return;
                            }
                            if (first.get()) {
                                turn.showContent();
                            }
                            turn.setError("I couldn't complete that request. " + safeMessage(error), lastUserText, this::retryLast);
                            persist("error", turn.rawText(), modelId, extras(modelId, traces, usage[0], started.get(), firstToken.get(), reasoningBuffer.toString(), generationId[0]));
                            turn.finishStreaming();
                            finishStream();
                        }),
                        () -> {
                            AssistantChatService.ThinkSplitter.Piece tail = splitter.flush();
                            if (!tail.reasoning().isBlank()) {
                                synchronized (reasoningBuffer) {
                                    reasoningBuffer.append(tail.reasoning());
                                }
                            }
                            if (!tail.answer().isEmpty()) {
                                synchronized (response) {
                                    response.append(tail.answer());
                                }
                            }
                            long duration = (System.nanoTime() - started.get()) / 1_000_000L;
                            Integer ttft = firstToken.get() == 0 ? null : (int) ((firstToken.get() - started.get()) / 1_000_000L);
                            String responseText = response.toString();
                            String reasoningText = reasoningBuffer.toString();
                            ChatMessageEntity saved = persist("assistant", responseText, modelId,
                                    extras(modelId, traces, usage[0], started.get(), firstToken.get(), reasoningText, generationId[0]));
                            addSessionCost(modelId, usage[0]);
                            final String tailReasoning = tail.reasoning();
                            access(ui, () -> {
                                if (!turn.close()) {
                                    return;
                                }
                                if (first.get()) {
                                    turn.showContent();
                                }
                                if (!tailReasoning.isBlank()) {
                                    turn.addReasoning(tailReasoning);
                                }
                                turn.setMarkdown(responseText);
                                turn.setStats(usage[0], catalog.estimateUsd(modelId, usage[0].promptTokens(), usage[0].completionTokens()), ttft, duration);
                                turn.setMentions(ChatEntityLinker.mentions(responseText, squadNames(), cachedClubNames), this::openPlayer, this::openClub);
                                turn.setCitations(traces);
                                turn.setFollowUps(this::sendPrompt);
                                rememberAssistant(responseText);
                                turn.finishStreaming();
                                turn.bindFeedback(saved);
                                pingIfTabHidden(responseText);
                                enrichFromOpenRouter(ui, turn, saved, generationId[0], ttft, duration);
                                finishStream();
                                refreshSessions();
                                scrollToLatest();
                            });
                        });
    }

    private List<AssistantChatService.ChatTurn> historyForModel() {
        return historyForModel(history);
    }

    private static List<AssistantChatService.ChatTurn> historyForModel(List<AssistantChatService.ChatTurn> turns) {
        return List.copyOf(turns);
    }

    private ChatSessionService.MessageExtras extras(
            String modelId,
            List<AssistantChatService.ToolTrace> traces,
            AssistantChatService.UsageSnapshot usage,
            long startedNs,
            long firstTokenNs,
            String reasoning,
            String generationId) {
        Integer ttft = firstTokenNs == 0 ? null : (int) ((firstTokenNs - startedNs) / 1_000_000L);
        int duration = (int) ((System.nanoTime() - startedNs) / 1_000_000L);
        Double cost = catalog.estimateUsd(modelId, usage.promptTokens(), usage.completionTokens());
        return new ChatSessionService.MessageExtras(
                tracesJson(traces),
                usage.promptTokens(),
                usage.completionTokens(),
                cost,
                ttft,
                duration,
                reasoning == null || reasoning.isBlank() ? null : reasoning,
                generationId == null || generationId.isBlank() ? null : generationId.strip());
    }

    private void enrichFromOpenRouter(
            UI ui,
            AssistantTurn turn,
            ChatMessageEntity saved,
            String generationId,
            Integer ttft,
            long durationMs) {
        if (saved == null || generationId == null || generationId.isBlank()) {
            return;
        }
        catalog.lookupGeneration(settings.openRouterApiKey(), generationId)
                .thenAccept(lookup -> access(ui, () -> {
                    if (lookup == null || lookup == OpenRouterModelCatalog.GenerationLookup.EMPTY) {
                        return;
                    }
                    String sessionId = saved.getSessionId();
                    ChatMessageEntity updated = sessions.updateGeneration(
                            sessionId, saved.getOrdinal(), lookup, lookup.reasoning());
                    if (!sessionId.equals(conversationId)) {
                        return;
                    }
                    AssistantChatService.UsageSnapshot next = new AssistantChatService.UsageSnapshot(
                            lookup.promptTokens() != null ? lookup.promptTokens() : saved.getPromptTokens(),
                            lookup.completionTokens() != null ? lookup.completionTokens() : saved.getCompletionTokens(),
                            lookup.reasoningTokens());
                    Double cost = lookup.totalCost() != null ? lookup.totalCost() : saved.getCostUsd();
                    if (lookup.totalCost() != null) {
                        double previous = saved.getCostUsd() == null ? 0 : saved.getCostUsd();
                        sessionCostUsd += lookup.totalCost() - previous;
                        sessionCost.setText(sessionCostUsd <= 0 ? "" : String.format("Session $%.4f", sessionCostUsd));
                    }
                    turn.setStats(next, cost, ttft, durationMs);
                    if (lookup.reasoning() != null && !lookup.reasoning().isBlank()) {
                        turn.appendReasoningOnce(lookup.reasoning());
                    }
                    if (updated != null && updated.getGenerationId() != null) {
                        turn.setGenerationId(updated.getGenerationId());
                    }
                }));
    }

    private static String tracesJson(List<AssistantChatService.ToolTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (AssistantChatService.ToolTrace trace : traces) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", trace.name());
            item.put("label", trace.label());
            item.put("input", trace.input());
            item.put("output", trace.output());
            item.put("elapsedMs", trace.elapsedMs());
            items.add(item);
        }
        try {
            return JSON.writeValueAsString(items);
        } catch (JacksonException ex) {
            return null;
        }
    }

    private ChatMessageEntity persist(String role, String body, String model, ChatSessionService.MessageExtras extras) {
        ensureSession();
        return sessions.append(conversationId, role, body,
                model == null || model.isBlank() ? settings.openRouterModel() : model, extras);
    }

    private Div userCard(String text, int ordinal) {
        Div message = new Div();
        message.addClassName("chat-message");
        message.addClassName("chat-message-user");
        Span meta = new Span("You · " + LocalTime.now().format(TIME_FORMAT) + asOfStamp());
        meta.addClassName("chat-message-meta");
        Button copy = iconButton(VaadinIcon.COPY, "Copy", () -> copyText(text));
        Button edit = iconButton(VaadinIcon.EDIT, "Edit", () -> editUser(text, ordinal));
        Button delete = iconButton(VaadinIcon.TRASH, "Delete", () -> deleteFrom(ordinal));
        HorizontalLayout heading = new HorizontalLayout(meta, copy, edit, delete);
        heading.setWidthFull();
        heading.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        heading.addClassName("chat-message-heading");
        Span body = new Span(text);
        body.addClassName("chat-message-body");
        message.add(heading, body);
        return message;
    }

    private void editUser(String text, int ordinal) {
        pendingReplaceFrom = ordinal;
        input.setValue(text);
        input.focus();
    }

    private void editLastUser() {
        if (input.getValue() != null && !input.getValue().isBlank()) {
            return;
        }
        int lastUser = history.size() - 1;
        while (lastUser >= 0 && !history.get(lastUser).user()) {
            lastUser--;
        }
        if (lastUser >= 0 && lastUserOrdinal >= 0) {
            pendingReplaceFrom = lastUserOrdinal;
            input.setValue(history.get(lastUser).text());
        }
    }

    private void deleteFrom(int ordinal) {
        if (conversationId == null) {
            return;
        }
        sessions.deleteFrom(conversationId, ordinal);
        reloadTranscript();
    }

    private void retryLast() {
        retry(lastUserText);
    }

    private void retry(String userText) {
        if (userText == null || userText.isBlank() || activeStream != null) {
            return;
        }
        if (dailyCapBlocked(userText)) {
            return;
        }
        streamUserMessage(userText, false);
    }

    private void regenerateFrom(String userText, int userOrdinal) {
        if (userText == null || userText.isBlank() || activeStream != null) {
            return;
        }
        if (conversationId != null && userOrdinal >= 0) {
            sessions.deleteFrom(conversationId, userOrdinal + 1);
            reloadTranscript();
        }
        streamUserMessage(userText, false);
    }

    private void rememberAssistant(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        history.add(new AssistantChatService.ChatTurn(false, text));
    }

    private void stopStream() {
        stopStream(true);
    }

    private void stopStream(boolean drainQueue) {
        AssistantTurn turn = activeTurn;
        if (activeStream != null) {
            activeStream.dispose();
            activeStream = null;
        }
        if (turn != null && turn.close()) {
            String stoppedText = turn.hasContent() ? turn.rawText() : "Stopped.";
            if (!turn.hasContent()) {
                turn.showContent();
                turn.setMarkdown("Stopped.");
            } else {
                turn.paint();
            }
            rememberAssistant(stoppedText);
            persist("assistant", stoppedText, currentModel, extras(currentModel,
                    List.of(),
                    new AssistantChatService.UsageSnapshot(null, null), System.nanoTime(), 0, turn.reasoningText(), turn.generationId()));
            turn.finishStreaming();
        }
        finishStream(drainQueue);
    }

    private void finishStream() {
        finishStream(true);
    }

    private void finishStream(boolean drainQueue) {
        activeStream = null;
        activeTurn = null;
        updateConfigurationState();
        updateComposerHint();
        if (!drainQueue || !isAttached() || queuedMessages.isEmpty() || !chat.configured()) {
            return;
        }
        String next = queuedMessages.poll();
        Double estimate = catalog.estimateUsd(
                settings.openRouterModel(),
                catalog.estimatePromptTokens(next, history.stream().map(AssistantChatService.ChatTurn::text).reduce("", String::concat)),
                600);
        if (dailyCapBlocked(next, estimate)) {
            return;
        }
        if (pendingReplaceFrom != null && conversationId != null) {
            sessions.deleteFrom(conversationId, pendingReplaceFrom);
            pendingReplaceFrom = null;
            reloadTranscript();
        }
        streamUserMessage(next, true, null);
    }

    private boolean tryFallback() {
        String next = null;
        for (String id : settings.openRouterFallbackModels()) {
            if (id != null && !id.isBlank() && !triedModels.contains(id)) {
                next = id;
                break;
            }
        }
        if (next == null) {
            return false;
        }
        pendingFallbackModel = next;
        Notification.show("Retrying with " + next, 2200, Notification.Position.BOTTOM_CENTER)
                .addClassName("app-toast");
        return true;
    }

    private void sendPrompt(String prompt) {
        if (prompt == null || prompt.isBlank() || !chat.configured() || activeStream != null) {
            return;
        }
        if (dailyCapBlocked(prompt)) {
            return;
        }
        streamUserMessage(prompt, true, null);
    }

    private void confirmNewChat() {
        if (!hasMessages()) {
            newChat();
            return;
        }
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Start a new chat?");
        dialog.setText("The current conversation stays in the sidebar.");
        dialog.setConfirmText("New chat");
        dialog.setCancelText("Cancel");
        dialog.setCancelable(true);
        dialog.addConfirmListener(event -> newChat());
        dialog.open();
    }

    private void exportMarkdown() {
        StringBuilder markdown = new StringBuilder("# FM AI chat\n\n");
        for (AssistantChatService.ChatTurn turn : history) {
            markdown.append(turn.user() ? "## You\n\n" : "## FM AI\n\n")
                    .append(turn.text() == null ? "" : turn.text())
                    .append("\n\n");
        }
        UI ui = getUI().orElse(null);
        if (ui == null) {
            return;
        }
        ui.getPage().executeJs("""
                const blob = new Blob([$0], {type: 'text/markdown;charset=utf-8'});
                const url = URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.href = url;
                link.download = 'fm-ai-chat.md';
                link.click();
                setTimeout(() => URL.revokeObjectURL(url), 1000);
                """, markdown.toString());
        Notification.show("Markdown downloaded", 1400, Notification.Position.BOTTOM_CENTER)
                .addClassName("app-toast");
    }

    private AssistantChatService.ChatGrounding grounding() {
        SnapshotHeartbeat.Status status = SnapshotHeartbeat.from(players.metadata(), players.countPlayers());
        Object gameDate = players.metadata().get("game_date");
        String date = gameDate == null ? "" : String.valueOf(gameDate).strip();
        return new AssistantChatService.ChatGrounding(
                sessionClubName(),
                settings.currency().label() + " (" + settings.currency().symbol() + ")",
                ChatUiContext.view(),
                ChatUiContext.filters(),
                date,
                status.empty(),
                status.stale(),
                settings.chatInstructions());
    }

    private String asOfStamp() {
        Object gameDate = players.metadata().get("game_date");
        if (gameDate != null && !String.valueOf(gameDate).isBlank()) {
            return " · as of " + String.valueOf(gameDate).strip();
        }
        if (players.countPlayers() > 0) {
            return " · as of " + GameDateFinder.DEFAULT_GAME_DATE + " (season baseline)";
        }
        return "";
    }

    private double todaySpendUsd() {
        OffsetDateTime start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        return sessions.spendUsdSince(start);
    }

    private boolean dailyCapBlocked(String message) {
        Double estimate = catalog.estimateUsd(
                settings.openRouterModel(),
                catalog.estimatePromptTokens(message, history.stream().map(AssistantChatService.ChatTurn::text).reduce("", String::concat)),
                600);
        return dailyCapBlocked(message, estimate);
    }

    private boolean dailyCapBlocked(String message, Double estimate) {
        String blocked = ChatSessionService.blockIfOverCap(settings.dailySpendCapUsd(), todaySpendUsd(), estimate);
        if (blocked == null) {
            return false;
        }
        Notification.show(blocked, 4000, Notification.Position.MIDDLE)
                .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
        return true;
    }

    private void newChat() {
        stopStream(false);
        restoreQueuedText();
        if (conversationId != null) {
            tacticContexts.forgetConversation(conversationId);
        }
        ChatSessionEntity created = sessions.create(settings.openRouterModel());
        conversationId = created.getId();
        settings.saveLastChatSessionId(conversationId);
        history.clear();
        sessionCostUsd = 0;
        sessionCost.setText("");
        lastUserText = "";
        lastUserOrdinal = -1;
        pendingReplaceFrom = null;
        queuedMessages.clear();
        triedModels.clear();
        pendingFallbackModel = "";
        transcript.removeAll();
        transcript.add(unconfigured, welcome);
        refreshStarters();
        refreshSessions();
        updateConfigurationState();
    }

    private void openOrRestoreSession() {
        String last = settings.lastChatSessionId();
        ChatSessionEntity session = sessions.find(last).orElse(null);
        if (session == null) {
            newChat();
            return;
        }
        conversationId = session.getId();
        reloadTranscript();
    }

    private void openSession(String id) {
        stopStream(false);
        restoreQueuedText();
        pendingReplaceFrom = null;
        lastUserText = "";
        lastUserOrdinal = -1;
        conversationId = id;
        settings.saveLastChatSessionId(id);
        reloadTranscript();
        refreshSessions();
    }

    private void restoreQueuedText() {
        String queued = queuedMessages.poll();
        if (queued != null && !queued.isBlank()
                && (input.getValue() == null || input.getValue().isBlank())) {
            input.setValue(queued);
        }
        if (!queuedMessages.isEmpty()) {
            Notification.show(
                    queuedMessages.size() + " queued message(s) were returned to the composer for review.",
                    3500,
                    Notification.Position.BOTTOM_CENTER);
        }
        queuedMessages.clear();
    }

    private void submitPendingPrompt(UI ui) {
        if (pendingPrompt.isBlank() || !chat.configured()) {
            return;
        }
        String prompt = pendingPrompt;
        pendingPrompt = "";
        if (ui != null) {
            ui.getPage().getHistory().replaceState(null, "chat");
        }
        input.setValue(prompt);
        send();
    }

    private void reloadTranscript() {
        history.clear();
        sessionCostUsd = 0;
        lastUserText = "";
        lastUserOrdinal = -1;
        transcript.removeAll();
        transcript.add(unconfigured, welcome);
        if (conversationId == null) {
            updateConfigurationState();
            return;
        }
        List<ChatMessageEntity> rows = sessions.messages(conversationId);
        String associatedUserText = "";
        int associatedUserOrdinal = -1;
        for (ChatMessageEntity row : rows) {
            if ("user".equals(row.getRole())) {
                lastUserText = row.getBody();
                lastUserOrdinal = row.getOrdinal();
                associatedUserText = row.getBody();
                associatedUserOrdinal = row.getOrdinal();
                history.add(new AssistantChatService.ChatTurn(true, row.getBody()));
                transcript.add(userCard(row.getBody(), row.getOrdinal()));
            } else if ("assistant".equals(row.getRole())) {
                history.add(new AssistantChatService.ChatTurn(false, row.getBody()));
                AssistantTurn turn = new AssistantTurn(row.getModel(), associatedUserText, associatedUserOrdinal);
                turn.showContent();
                turn.setMarkdown(row.getBody());
                turn.finishStreaming();
                if (row.getCostUsd() != null) {
                    sessionCostUsd += row.getCostUsd();
                }
                turn.setStats(
                        new AssistantChatService.UsageSnapshot(row.getPromptTokens(), row.getCompletionTokens()),
                        row.getCostUsd(),
                        row.getTtftMs(),
                        row.getDurationMs() == null ? 0 : row.getDurationMs());
                turn.setMentions(ChatEntityLinker.mentions(row.getBody(), squadNames(), cachedClubNames), this::openPlayer, this::openClub);
                turn.setCitationsFromJson(row.getToolsJson());
                turn.setStoredReasoning(row.getReasoning());
                turn.addStoredTraces(row.getToolsJson());
                turn.bindFeedback(row);
                transcript.add(turn.root);
            } else if ("error".equals(row.getRole())) {
                AssistantTurn turn = new AssistantTurn(row.getModel(), associatedUserText, associatedUserOrdinal);
                String retryText = associatedUserText;
                turn.setError(row.getBody(), retryText, () -> retry(retryText));
                transcript.add(turn.root);
            }
        }
        addSessionCost("", new AssistantChatService.UsageSnapshot(null, null));
        welcome.setVisible(chat.configured() && rows.isEmpty());
        updateConfigurationState();
        scrollToLatest();
    }

    private void refreshSessions() {
        sessionList.removeAll();
        LocalDate lastDay = null;
        for (ChatSessionEntity session : sessions.search(sessionSearch.getValue())) {
            LocalDate day = session.getUpdatedAt() == null ? LocalDate.now() : session.getUpdatedAt().toLocalDate();
            if (!day.equals(lastDay)) {
                Span label = new Span(day.format(DAY_FORMAT));
                label.addClassName("chat-session-day");
                sessionList.add(label);
                lastDay = day;
            }
            Button open = new Button(session.getTitle() == null ? ChatSessionService.DEFAULT_TITLE : session.getTitle());
            open.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            open.addClassName("chat-session-item");
            if (session.getId().equals(conversationId)) {
                open.addClassName("chat-session-active");
            }
            open.addClickListener(event -> openSession(session.getId()));
            Button rename = iconButton(VaadinIcon.EDIT, "Rename", () -> renameSession(session));
            Button delete = iconButton(VaadinIcon.TRASH, "Delete", () -> {
                sessions.delete(session.getId());
                if (session.getId().equals(conversationId)) {
                    newChat();
                } else {
                    refreshSessions();
                }
            });
            HorizontalLayout row = new HorizontalLayout(open, rename, delete);
            row.setWidthFull();
            row.setFlexGrow(1, open);
            sessionList.add(row);
        }
    }

    private void renameSession(ChatSessionEntity session) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Rename chat");
        TextField title = new TextField();
        title.setWidthFull();
        title.setValue(session.getTitle());
        Button save = new Button("Save", event -> {
            sessions.rename(session.getId(), title.getValue());
            dialog.close();
            refreshSessions();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.add(title);
        dialog.getFooter().add(save);
        dialog.open();
        title.focus();
    }

    private void ensureSession() {
        if (conversationId != null && sessions.find(conversationId).isPresent()) {
            return;
        }
        ChatSessionEntity created = sessions.create(settings.openRouterModel());
        conversationId = created.getId();
        settings.saveLastChatSessionId(conversationId);
    }

    private void addSessionCost(String modelId, AssistantChatService.UsageSnapshot usage) {
        Double extra = catalog.estimateUsd(modelId, usage.promptTokens(), usage.completionTokens());
        if (extra != null) {
            sessionCostUsd += extra;
        }
        sessionCost.setText(sessionCostUsd <= 0 ? "" : String.format("Session $%.4f", sessionCostUsd));
    }

    private void maybeOfferSlash(String value) {
        if (value == null || !value.startsWith("/") || value.contains(" ")) {
            slash.setVisible(false);
            return;
        }
        String token = value.strip().toLowerCase();
        List<String> items = ChatSlashCommands.COMMANDS.stream()
                .filter(command -> command.name().startsWith(token) || "/".equals(token))
                .map(command -> command.name() + " — " + command.hint())
                .toList();
        slash.setItems(items);
        slash.setVisible(!items.isEmpty());
    }

    private void maybeOfferMention(String value) {
        if (value == null) {
            mention.setVisible(false);
            return;
        }
        int at = value.lastIndexOf('@');
        if (at < 0) {
            mention.setVisible(false);
            return;
        }
        String prefix = value.substring(at + 1).toLowerCase();
        List<String> names = squadNames().stream()
                .filter(name -> name.toLowerCase().contains(prefix))
                .limit(12)
                .toList();
        mention.setItems(names);
        mention.setVisible(!names.isEmpty());
    }

    private List<String> squadNames() {
        return cachedSquadNames;
    }

    private List<String> computeSquadNames() {
        String club = sessionClubName();
        if (club == null || club.isBlank()) {
            return List.of();
        }
        return players.findPlayerEntities(PlayerFilterCriteria.clubOnly(club)).stream()
                .map(PlayerEntity::getName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
    }

    private String sessionClubName() {
        return SessionClub.resolved(settings, SessionClub.names(clubs));
    }

    private void refreshCachedNames() {
        cachedSquadNames = computeSquadNames();
        cachedClubNames = clubs.findNames();
    }

    private void openPlayer(String name) {
        PlayerDossier.openNamed(tools, name, settings.currency(), sessionClubName());
    }

    private void openClub(String name) {
        ChatLaunch.open("Summarize " + name + " from the save: reputation, budget and notable players.");
    }

    private void openCommandPalette() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Commands");
        VerticalLayout list = new VerticalLayout();
        list.add(new Button("New chat", event -> {
            dialog.close();
            confirmNewChat();
        }));
        for (ChatSlashCommands.Command command : ChatSlashCommands.COMMANDS) {
            list.add(new Button(command.name() + " — " + command.hint(), event -> {
                input.setValue(command.prompt());
                dialog.close();
                send();
            }));
        }
        for (ChatSessionEntity session : sessions.list().stream().limit(6).toList()) {
            list.add(new Button(session.getTitle(), event -> {
                dialog.close();
                openSession(session.getId());
            }));
        }
        dialog.add(list);
        dialog.open();
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

    private static Button iconButton(VaadinIcon icon, String label, Runnable action) {
        Button button = new Button(icon.create(), event -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.getElement().setAttribute("aria-label", label);
        button.addClassName("chat-copy");
        return button;
    }

    private static void copyText(String text) {
        UI ui = UI.getCurrent();
        if (ui != null && text != null) {
            ui.getPage().executeJs("navigator.clipboard.writeText($0)", text);
        }
        Notification.show("Copied", 1200, Notification.Position.BOTTOM_CENTER).addClassName("app-toast");
    }

    @ClientCallable
    public void receivePastedImage(String name, String dataUrl) {
        if (dataUrl == null || !dataUrl.contains(",")) {
            return;
        }
        int maxChars = 10 * 1024 * 1024;
        if (dataUrl.length() > maxChars) {
            Notification.show("Pasted image too large (max 10 MB)", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
            return;
        }
        String fileName = name == null || name.isBlank() ? "pasted.png" : name;
        String base64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
        UI ui = getUI().orElse(null);
        CompletableFuture.supplyAsync(() -> {
            try {
                return (byte[]) Base64.getDecoder().decode(base64);
            } catch (RuntimeException ex) {
                return null;
            }
        }).thenAccept(bytes -> access(ui, () -> {
            if (bytes == null) {
                Notification.show("Could not read pasted image", 2500, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
                return;
            }
            importChatTacticFiles(Map.of(fileName, bytes), "Pasted screenshot added as tactic context");
        }));
    }

    private void importChatTacticFiles(Map<String, byte[]> files, String successMessage) {
        if (files == null || files.isEmpty()) {
            return;
        }
        UI ui = getUI().orElse(null);
        if (ui == null) {
            return;
        }
        tacticPanel.setImportBusy(true);
        Thread.ofVirtual().name("chat-tactic-context-import").start(() -> {
            try {
                tacticContexts.loadUploads(files);
                access(ui, () -> {
                    tacticPanel.setImportBusy(false);
                    tacticPanel.refreshCurrent();
                    Notification.show(successMessage, 1800, Notification.Position.BOTTOM_CENTER)
                            .addClassName("app-toast");
                });
            } catch (RuntimeException ex) {
                access(ui, () -> {
                    tacticPanel.setImportBusy(false);
                    Notification.show(ex.getMessage() == null ? "Tactic context update failed" : ex.getMessage(),
                            4000, Notification.Position.MIDDLE);
                });
            }
        });
    }

    private void refreshPinnedButton() {
        boolean pinned = model.getValue() != null && settings.pinnedModels().contains(model.getValue());
        pinModel.getElement().setAttribute("title", pinned ? "Unpin model" : "Pin model");
        pinModel.getStyle().set("color", pinned ? "var(--fmai-accent)" : "");
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank() ? "Please try again." : error.getMessage();
    }

    private void pingIfTabHidden(String reply) {
        UI ui = getUI().orElse(null);
        if (ui == null) {
            return;
        }
        String preview = reply == null ? "" : reply.replaceAll("\\s+", " ").strip();
        if (preview.length() > 120) {
            preview = preview.substring(0, 117) + "…";
        }
        Boolean pref = settings.desktopNotify();
        String prefText = pref == null ? "" : Boolean.toString(pref);
        ui.getPage().executeJs("""
                const body = $0;
                const pref = $1;
                if (!document.hidden) {
                  return;
                }
                const previous = document.title;
                document.title = 'Reply ready · ' + previous;
                setTimeout(() => {
                  if (document.title.startsWith('Reply ready')) {
                    document.title = previous;
                  }
                }, 5000);
                const notify = () => {
                  if (Notification.permission !== 'granted') {
                    return;
                  }
                  try {
                    new Notification('FM AI reply ready', { body, silent: true });
                  } catch (error) {}
                };
                if (pref !== 'true') {
                  return;
                }
                if (Notification.permission === 'default') {
                  Notification.requestPermission().then(permission => {
                    $2.$server.receiveNotifyPermission(permission);
                    if (permission === 'granted') {
                      notify();
                    }
                  });
                  return;
                }
                notify();
                """, preview, prefText, getElement());
    }

    @ClientCallable
    public void receiveNotifyPermission(String permission) {
        settings.saveDesktopNotify("granted".equalsIgnoreCase(permission));
    }

    private final class AssistantTurn {
        private final Div root = new Div();
        private final Div typing = new Div();
        private final Span typingLabel = new Span("Thinking");
        private final Markdown body = new Markdown("");
        private final Pre reasoningBody = new Pre("");
        private final Details reasoning = new Details("Thinking", reasoningBody);
        private final Div traces = new Div();
        private final Div chips = new Div();
        private final Span stats = new Span();
        private final Button copy = new Button(VaadinIcon.COPY.create());
        private final Button thumbsUp = new Button(VaadinIcon.THUMBS_UP.create());
        private final Button thumbsDown = new Button(VaadinIcon.THUMBS_DOWN.create());
        private final Button retry = new Button("Retry");
        private final Set<String> tools = new LinkedHashSet<>();
        private final String userText;
        private final int userOrdinal;
        private String raw = "";
        private String reasoningRaw = "";
        private String generationId = "";
        private boolean contentVisible;
        private final AtomicBoolean closed = new AtomicBoolean();

        private AssistantTurn(String modelId, String userText, int userOrdinal) {
            this.userText = userText == null ? "" : userText;
            this.userOrdinal = userOrdinal;
            root.addClassName("chat-message");
            root.addClassName("chat-message-assistant");
            String model = modelId == null || modelId.isBlank() ? settings.openRouterModel() : modelId;
            Span meta = new Span("FM AI · " + shortModel(model) + " · " + LocalTime.now().format(TIME_FORMAT) + asOfStamp());
            meta.addClassName("chat-message-meta");
            copy.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            copy.addClassName("chat-copy");
            copy.getElement().setAttribute("aria-label", "Copy reply");
            copy.setVisible(false);
            copy.addClickListener(event -> copyText(raw));
            thumbsUp.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            thumbsDown.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            thumbsUp.addClassName("chat-copy");
            thumbsDown.addClassName("chat-copy");
            thumbsUp.getElement().setAttribute("aria-label", "Helpful");
            thumbsDown.getElement().setAttribute("aria-label", "Not helpful");
            thumbsUp.setVisible(false);
            thumbsDown.setVisible(false);
            Button regenerate = iconButton(VaadinIcon.REFRESH, "Regenerate", () -> regenerateFrom(userText, userOrdinal));
            retry.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            retry.setVisible(false);
            HorizontalLayout heading = new HorizontalLayout(meta, copy, thumbsUp, thumbsDown, regenerate, retry);
            heading.setWidthFull();
            heading.setAlignItems(FlexComponent.Alignment.CENTER);
            heading.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
            heading.addClassName("chat-message-heading");
            heading.getStyle().set("justify-content", "space-between");

            typing.addClassName("chat-typing");
            typingLabel.addClassName("chat-typing-label");
            Div dots = new Div(dot(), dot(), dot());
            dots.addClassName("chat-typing-dots");
            typing.add(dots, typingLabel);
            body.addClassName("chat-markdown");
            body.addClassName("chat-streaming");
            body.getElement().setAttribute("aria-live", "polite");
            body.getElement().setAttribute("aria-busy", "true");
            body.setVisible(false);
            traces.addClassName("chat-traces");
            chips.addClassName("chat-chips");
            stats.addClassName("chat-stats");
            reasoning.setOpened(false);
            reasoning.setVisible(false);
            reasoning.addClassName("chat-reasoning");
            root.add(heading, typing, reasoning, traces, body, chips, stats);
        }

        private static Span dot() {
            Span dot = new Span();
            dot.addClassName("chat-typing-dot");
            return dot;
        }

        private boolean close() {
            return closed.compareAndSet(false, true);
        }

        private void addReasoning(String text) {
            if (text == null || text.isBlank() || closed.get()) {
                return;
            }
            reasoning.setVisible(true);
            reasoningBody.setText(reasoningBody.getText() + text);
            if (reasoningRaw == null) {
                reasoningRaw = "";
            }
            reasoningRaw += text;
        }

        private String reasoningText() {
            return reasoningRaw == null ? "" : reasoningRaw;
        }

        private void setStoredReasoning(String text) {
            if (text == null || text.isBlank()) {
                return;
            }
            reasoningRaw = text;
            reasoningBody.setText(text);
            reasoning.setVisible(true);
        }

        private void appendReasoningOnce(String text) {
            if (text == null || text.isBlank()) {
                return;
            }
            String next = text.strip();
            if (reasoningRaw != null && reasoningRaw.contains(next)) {
                return;
            }
            reasoningRaw = (reasoningRaw == null ? "" : reasoningRaw) + next;
            reasoningBody.setText(reasoningRaw);
            reasoning.setVisible(true);
        }

        private void setGenerationId(String id) {
            generationId = id == null ? "" : id.strip();
        }

        private String generationId() {
            return generationId;
        }

        private void addTool(String name) {
            if (name == null || name.isBlank() || closed.get()) {
                return;
            }
            tools.add(name.strip());
            if (!contentVisible) {
                typingLabel.setText(String.join(" · ", tools));
            }
        }

        private void addTrace(AssistantChatService.ToolTrace trace) {
            Details details = new Details(trace.label() + " · " + trace.elapsedMs() + " ms", new Pre(
                    (trace.input() == null ? "" : trace.input()) + "\n\n" + (trace.output() == null ? "" : trace.output())));
            details.setOpened(false);
            traces.add(details);
        }

        private void addStoredTraces(String toolsJson) {
            if (toolsJson == null || toolsJson.isBlank()) {
                return;
            }
            Details details = new Details("Tool traces", new Pre(toolsJson));
            details.setOpened(false);
            traces.add(details);
        }

        private void showContent() {
            contentVisible = true;
            typing.addClassName("chat-typing-compact");
            typingLabel.setText("Writing");
            body.setVisible(true);
            copy.setVisible(true);
        }

        private void finishStreaming() {
            typing.setVisible(false);
            body.removeClassName("chat-streaming");
            body.getElement().setAttribute("aria-busy", "false");
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

        private void discardProgressText() {
            raw = "";
            body.setContent("");
        }

        private void paint() {
            body.setContent(ChatMarkdown.sanitize(raw));
            enhanceCodeBlocks();
        }

        private void enhanceCodeBlocks() {
            body.getElement().executeJs("""
                    const root = this;
                    const highlight = (code) => {
                      if (!code || code.dataset.hl) {
                        return;
                      }
                      if (window.hljs && typeof window.hljs.highlightElement === 'function') {
                        try {
                          window.hljs.highlightElement(code);
                          code.dataset.hl = '1';
                          return;
                        } catch (error) {}
                      }
                      const cls = [...code.classList].find(c => c.startsWith('language-'));
                      const name = cls ? cls.slice(9).toLowerCase() : '';
                      let html = code.innerHTML;
                      const paint = (re, cls) => { html = html.replace(re, m => '<span class="' + cls + '">' + m + '</span>'); };
                      if (name === 'json' || name === 'javascript' || name === 'js') {
                        paint(/&quot;(?:\\\\.|[^&])*&quot;/g, 'hl-str');
                        paint(/\\b(true|false|null)\\b/g, 'hl-kw');
                        paint(/\\b-?\\d+(?:\\.\\d+)?\\b/g, 'hl-num');
                      } else if (name === 'java' || name === 'kotlin') {
                        paint(/\\b(class|return|new|if|else|for|void|public|private|static|final|null|true|false)\\b/g, 'hl-kw');
                        paint(/&quot;(?:\\\\.|[^&])*&quot;/g, 'hl-str');
                      } else if (name === 'sql') {
                        paint(/\\b(SELECT|FROM|WHERE|AND|OR|JOIN|LEFT|RIGHT|INNER|ON|AS|LIMIT|NULL|INSERT|UPDATE)\\b/gi, 'hl-kw');
                      } else if (name === 'xml' || name === 'html') {
                        paint(/&lt;\\/?[\\w:-]+/g, 'hl-kw');
                      } else if (name === 'bash' || name === 'sh' || name === 'shell') {
                        paint(/\\b(if|then|fi|echo|export|for|do|done)\\b/g, 'hl-kw');
                      } else {
                        return;
                      }
                      code.innerHTML = html;
                      code.dataset.hl = '1';
                    };
                    const attach = () => {
                      root.querySelectorAll('pre').forEach(pre => {
                        const code = pre.querySelector('code');
                        highlight(code);
                        if (pre.dataset.copyReady) {
                          return;
                        }
                        pre.dataset.copyReady = '1';
                        if (code) {
                          const lang = [...code.classList].find(c => c.startsWith('language-'));
                          if (lang) {
                            const tag = document.createElement('span');
                            tag.className = 'chat-code-lang';
                            tag.textContent = lang.replace('language-', '');
                            pre.appendChild(tag);
                          }
                        }
                        const btn = document.createElement('button');
                        btn.type = 'button';
                        btn.className = 'chat-code-copy';
                        btn.textContent = 'Copy';
                        btn.setAttribute('aria-label', 'Copy code');
                        btn.addEventListener('click', event => {
                          event.stopPropagation();
                          const text = code ? code.innerText : pre.innerText;
                          navigator.clipboard.writeText(text);
                          btn.textContent = 'Copied';
                          setTimeout(() => { btn.textContent = 'Copy'; }, 1200);
                        });
                        pre.appendChild(btn);
                      });
                    };
                    attach();
                    requestAnimationFrame(attach);
                    """);
        }

        private void bindFeedback(ChatMessageEntity row) {
            if (row == null || !"assistant".equals(row.getRole())) {
                return;
            }
            int ordinal = row.getOrdinal();
            setGenerationId(row.getGenerationId());
            thumbsUp.setVisible(true);
            thumbsDown.setVisible(true);
            paintFeedback(row.getFeedback());
            thumbsUp.addClickListener(event -> paintFeedback(sessions.setFeedback(conversationId, ordinal, "up")));
            thumbsDown.addClickListener(event -> {
                String value = sessions.setFeedback(conversationId, ordinal, "down");
                paintFeedback(value);
                if ("down".equals(value) && generationId != null && !generationId.isBlank()) {
                    catalog.submitGenerationFeedback(settings.openRouterApiKey(), generationId, "incorrect_response")
                            .whenComplete((result, error) -> getUI().ifPresent(ui -> access(ui, () -> {
                                if (result != null && !result.ok() && (result.status() == 401 || result.status() == 403)) {
                                    stats.setText((stats.getText() == null || stats.getText().isBlank()
                                            ? "" : stats.getText() + " · ") + result.message());
                                }
                            })));
                }
            });
        }

        private void paintFeedback(String value) {
            thumbsUp.getElement().setAttribute("aria-pressed", Boolean.toString("up".equals(value)));
            thumbsDown.getElement().setAttribute("aria-pressed", Boolean.toString("down".equals(value)));
            thumbsUp.removeClassName("chat-feedback-on");
            thumbsDown.removeClassName("chat-feedback-on");
            if ("up".equals(value)) {
                thumbsUp.addClassName("chat-feedback-on");
            } else if ("down".equals(value)) {
                thumbsDown.addClassName("chat-feedback-on");
            }
        }

        private void setError(String message, String retryText, Runnable onRetry) {
            showContent();
            raw = message == null ? "" : message;
            body.setContent(ChatMarkdown.sanitize(raw));
            body.addClassName("chat-error");
            retry.setVisible(retryText != null && !retryText.isBlank());
            retry.addClickListener(event -> onRetry.run());
            finishStreaming();
        }

        private void setStats(AssistantChatService.UsageSnapshot usage, Double cost, Integer ttftMs, long durationMs) {
            List<String> parts = new ArrayList<>();
            if (ttftMs != null && ttftMs > 0) {
                parts.add(String.format("%.1fs", ttftMs / 1000.0));
            }
            if (usage != null && usage.completionTokens() != null && durationMs > 0) {
                parts.add(String.format("%.0f tok/s", usage.completionTokens() / Math.max(0.001, durationMs / 1000.0)));
            }
            if (usage != null && (usage.promptTokens() != null || usage.completionTokens() != null)) {
                parts.add((usage.promptTokens() == null ? 0 : usage.promptTokens())
                        + "+" + (usage.completionTokens() == null ? 0 : usage.completionTokens()) + " tok");
            }
            if (usage != null && usage.reasoningTokens() != null && usage.reasoningTokens() > 0) {
                parts.add(usage.reasoningTokens() + " reason");
            }
            if (cost != null && cost > 0) {
                parts.add(String.format("$%.4f", cost));
            }
            stats.setText(String.join(" · ", parts));
        }

        private void setFollowUps(java.util.function.Consumer<String> onPrompt) {
            if (players.countPlayers() <= 0) {
                return;
            }
            for (String prompt : List.of(
                    "Compare with another named club",
                    "Show the XI from the live formation",
                    "How much transfer budget is left?")) {
                Button chip = new Button(prompt, event -> onPrompt.accept(prompt));
                chip.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
                chip.addClassName("chat-chip");
                chips.add(chip);
            }
        }

        private void setMentions(List<String> names, java.util.function.Consumer<String> onPlayer, java.util.function.Consumer<String> onClub) {
            chips.removeAll();
            Set<String> clubsNames = new LinkedHashSet<>(cachedClubNames);
            for (String name : names) {
                Button chip = new Button(name);
                chip.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
                chip.addClassName("chat-chip");
                boolean club = clubsNames.stream().anyMatch(item -> item.equalsIgnoreCase(name));
                chip.addClickListener(event -> {
                    if (club) {
                        onClub.accept(name);
                    } else {
                        onPlayer.accept(name);
                    }
                });
                chips.add(chip);
            }
        }

        private void setCitations(List<AssistantChatService.ToolTrace> traces) {
            if (traces == null) {
                return;
            }
            for (AssistantChatService.ToolTrace trace : traces) {
                addCitationChip(trace.label() == null || trace.label().isBlank() ? trace.name() : trace.label());
            }
        }

        private void setCitationsFromJson(String toolsJson) {
            if (toolsJson == null || toolsJson.isBlank()) {
                return;
            }
            try {
                JsonNode root = JSON.readTree(toolsJson);
                if (root == null || !root.isArray()) {
                    return;
                }
                for (JsonNode node : root) {
                    JsonNode label = node.get("label");
                    if (label != null && !label.isNull()) {
                        addCitationChip(label.asText());
                    }
                }
            } catch (JacksonException ignored) {
            }
        }

        private void addCitationChip(String label) {
            if (label == null || label.isBlank()) {
                return;
            }
            Span chip = new Span("from " + label);
            chip.addClassName("chat-chip");
            chip.addClassName("chat-citation");
            chips.add(chip);
        }
    }

    private static String shortModel(String model) {
        if (model == null || model.isBlank()) {
            return "model";
        }
        int slash = model.lastIndexOf('/');
        return slash < 0 ? model : model.substring(slash + 1);
    }
}
