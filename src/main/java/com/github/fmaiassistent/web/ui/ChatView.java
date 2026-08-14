package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.service.AssistantChatService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "chat", layout = AppShell.class)
@PageTitle("Chat")
@CssImport("./styles/moneyball-view.css")
public class ChatView extends VerticalLayout {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<String> STARTERS = List.of(
            "Build my best XI from the live formation",
            "Find affordable wonderkids for my club",
            "Who should I sell or loan out?",
            "Compare my squad with the league's best");

    private final AssistantChatService chat;
    private final Div transcript = new Div();
    private final TextArea input = new TextArea();
    private final Button send = new Button("Send", VaadinIcon.PAPERPLANE.create());
    private final Button stop = new Button("Stop", VaadinIcon.STOP.create());
    private final Button clear = new Button("New chat", VaadinIcon.PLUS.create());
    private final Span status = new Span();
    private final Span typing = new Span("AI is thinking");
    private Disposable activeStream;
    private Div welcome;

    public ChatView(AssistantChatService chat) {
        this.chat = chat;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("moneyball-view");
        addClassName("chat-view");

        transcript.addClassName("chat-transcript");
        transcript.setWidthFull();
        transcript.getElement().setAttribute("aria-live", "polite");
        typing.addClassName("chat-typing");
        typing.setVisible(false);

        input.setPlaceholder("Ask about your squad, transfers, tactics, or a player...");
        input.setWidthFull();
        input.setMinHeight("4.5em");
        input.setMaxHeight("12em");
        input.setHelperText("Enter to send · Shift + Enter for a new line");
        input.addKeyDownListener(Key.ENTER, event -> {
            if (!event.getModifiers().contains(KeyModifier.SHIFT)) {
                send();
            }
        });

        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        send.addClickListener(event -> send());
        stop.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        stop.setEnabled(false);
        stop.addClickListener(event -> stopStream());
        clear.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        clear.addClickListener(event -> clearChat());

        Component workspace = workspace();
        add(header(), configurationBanner(), workspace, composer());
        setFlexGrow(1, workspace);
        updateConfigurationState();
    }

    private Component header() {
        Span hint = new Span("A calm second opinion for your next transfer, tactic, or team talk.");
        hint.addClassName("moneyball-hint");
        hint.setWidthFull();
        HorizontalLayout header = new HorizontalLayout(hint);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.addClassName("chat-header");
        return header;
    }

    private Component configurationBanner() {
        status.addClassName("chat-status");
        return status;
    }

    private Component workspace() {
        welcome = new Div();
        welcome.addClassName("chat-welcome");
        H3 title = new H3("What are we solving today?");
        Span copy = new Span("Ask naturally. I can inspect the loaded FM snapshot and use the same recruitment and squad tools as your MCP assistant.");
        copy.addClassName("chat-welcome-copy");
        welcome.add(title, copy, starterPrompts());
        transcript.add(welcome);

        VerticalLayout rail = new VerticalLayout();
        rail.addClassName("chat-rail");
        rail.setPadding(false);
        rail.setSpacing(false);
        rail.add(new Span("Shortcuts"));
        rail.add(new Span("Every answer is grounded in your latest saved snapshot. Load from RAM when your save changes."));
        rail.add(new Span("Tip: mention a budget, position, or club to make recommendations sharper."));

        HorizontalLayout workspace = new HorizontalLayout(transcript, rail);
        workspace.addClassName("chat-workspace");
        workspace.setWidthFull();
        workspace.setFlexGrow(1, transcript);
        return workspace;
    }

    private HorizontalLayout starterPrompts() {
        HorizontalLayout prompts = new HorizontalLayout();
        prompts.addClassName("chat-starters");
        prompts.setWidthFull();
        prompts.setWrap(true);
        for (String prompt : STARTERS) {
            Button button = new Button(prompt, event -> {
                input.setValue(prompt);
                send();
            });
            button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            button.addClassName("chat-starter");
            prompts.add(button);
        }
        return prompts;
    }

    private HorizontalLayout composer() {
        HorizontalLayout actions = new HorizontalLayout(input, send, stop, clear);
        actions.addClassName("chat-composer");
        actions.setWidthFull();
        actions.setAlignItems(FlexComponent.Alignment.END);
        actions.setFlexGrow(1, input);
        return actions;
    }

    private void updateConfigurationState() {
        if (!chat.configured()) {
            status.setText("Connect an OpenAI key in Settings to enable in-app chat. Your key stays in fm-ai-assistent.properties.");
            status.addClassName("chat-status-warning");
            send.setEnabled(false);
            input.setEnabled(false);
        } else {
            status.setText("Connected · responses stream live · FM snapshot tools are available");
            status.addClassName("chat-status-ready");
        }
    }

    private void send() {
        String message = input.getValue();
        if (message == null || message.isBlank() || activeStream != null) {
            return;
        }
        appendMessage("You", message.trim(), true);
        input.clear();
        welcome.setVisible(false);
        send.setEnabled(false);
        stop.setEnabled(true);
        typing.setVisible(true);
        if (typing.getParent().isEmpty()) {
            transcript.add(typing);
        }
        scrollToLatest();

        UI ui = getUI().orElse(null);
        Div assistant = messageBubble("FM AI", "", false);
        transcript.add(assistant);
        Span body = (Span) assistant.getChildren().skip(1).findFirst().orElseThrow();
        StringBuilder response = new StringBuilder();
        activeStream = chat.stream(message.trim())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        token -> access(ui, () -> {
                            response.append(token);
                            body.setText(response.toString());
                            scrollToLatest();
                        }),
                        error -> access(ui, () -> {
                            body.setText("I couldn't complete that request. " + safeMessage(error));
                            body.addClassName("chat-error");
                            finishStream();
                        }),
                        () -> access(ui, () -> {
                            finishStream();
                            scrollToLatest();
                        }));
    }

    private void appendMessage(String author, String text, boolean user) {
        transcript.add(messageBubble(author, text, user));
    }

    private Div messageBubble(String author, String text, boolean user) {
        Div message = new Div();
        message.addClassName("chat-message");
        message.addClassName(user ? "chat-message-user" : "chat-message-assistant");
        Span meta = new Span(author + " · " + LocalTime.now().format(TIME_FORMAT));
        meta.addClassName("chat-message-meta");
        Span body = new Span(text);
        body.addClassName("chat-message-body");
        message.add(meta, body);
        return message;
    }

    private void clearChat() {
        stopStream();
        transcript.removeAll();
        transcript.add(welcome);
        welcome.setVisible(true);
    }

    private void stopStream() {
        if (activeStream != null) {
            activeStream.dispose();
            activeStream = null;
        }
        finishStream();
    }

    private void finishStream() {
        activeStream = null;
        typing.setVisible(false);
        send.setEnabled(chat.configured());
        input.setEnabled(chat.configured());
        stop.setEnabled(false);
    }

    private void scrollToLatest() {
        transcript.getElement().executeJs("this.scrollTop = this.scrollHeight");
    }

    private void access(UI ui, Command action) {
        if (ui != null) {
            ui.access(action);
        }
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank() ? "Please try again." : error.getMessage();
    }
}
