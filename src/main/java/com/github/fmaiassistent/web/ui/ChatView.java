package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.service.AssistantChatService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import reactor.core.scheduler.Schedulers;

@Route("chat")
@PageTitle("Chat")
@CssImport("./styles/moneyball-view.css")
public class ChatView extends VerticalLayout {
    private final AssistantChatService chat;
    private final Div transcript = new Div();
    private final TextArea input = new TextArea();
    private final Button send = new Button("Send", VaadinIcon.PAPERPLANE.create());
    private final Span status = new Span();

    public ChatView(AssistantChatService chat) {
        this.chat = chat;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        addClassName("moneyball-view");
        transcript.getStyle().set("white-space", "pre-wrap");
        transcript.getStyle().set("overflow", "auto");
        transcript.setWidthFull();
        transcript.getStyle().set("flex-grow", "1");
        input.setWidthFull();
        input.setPlaceholder("Ask about your squad, transfers, or a pasted tactic...");
        input.setMinHeight("6em");
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        send.addClickListener(event -> send());
        add(header(), status, transcript, input, send);
        expand(transcript);
        if (!chat.configured()) {
            status.setText("No OpenAI API key. Add one in Settings, or point Codex/Claude at http://127.0.0.1:8080/mcp");
            send.setEnabled(false);
            input.setEnabled(false);
        } else {
            status.setText("Uses the same MCP tools as Codex/Claude. Replies stream into this page. Key stays in fm-ai-assistent.properties.");
        }
    }

    private Component header() {
        Span title = new Span("Chat");
        title.addClassName("moneyball-title");
        Span hint = new Span("In-app assistant. Optional: leave this unused and keep using MCP.");
        hint.addClassName("moneyball-hint");
        VerticalLayout titleBlock = new VerticalLayout(title, hint);
        titleBlock.setSpacing(false);
        titleBlock.setPadding(false);
        HorizontalLayout header = new HorizontalLayout(titleBlock, WorkspaceLinks.buttons());
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        return header;
    }

    private void send() {
        String message = input.getValue();
        if (message == null || message.isBlank()) {
            return;
        }
        append("You", message);
        input.clear();
        send.setEnabled(false);
        UI ui = getUI().orElse(null);
        Span assistant = new Span("Assistant\n");
        assistant.getStyle().set("display", "block");
        transcript.add(assistant);
        StringBuilder body = new StringBuilder("Assistant\n");
        chat.stream(message)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        token -> {
                            if (ui != null) {
                                ui.access(() -> {
                                    body.append(token);
                                    assistant.setText(body.toString());
                                });
                            }
                        },
                        error -> {
                            if (ui != null) {
                                ui.access(() -> {
                                    send.setEnabled(true);
                                    Notification.show(error.getMessage() == null ? "Chat failed" : error.getMessage(),
                                                    6000, Notification.Position.MIDDLE)
                                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                                });
                            }
                        },
                        () -> {
                            if (ui != null) {
                                ui.access(() -> {
                                    body.append("\n\n");
                                    assistant.setText(body.toString());
                                    send.setEnabled(true);
                                });
                            }
                        });
    }

    private void append(String who, String text) {
        Span block = new Span(who + "\n" + text + "\n\n");
        block.getStyle().set("display", "block");
        transcript.add(block);
    }
}
