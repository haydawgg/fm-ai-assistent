package com.github.fmaiassistent.web.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.FlexComponent;

/** Compact assistant module that preserves the current workspace context. */
public final class ContextualAssistantPanel {
    private ContextualAssistantPanel() {
    }

    public static void open(ContextualAssistantRequest request) {
        Dialog dialog = new Dialog();
        dialog.addClassName("contextual-assistant-dialog");
        dialog.setHeaderTitle("FM AI · contextual help");
        dialog.setWidth("min(420px, calc(100vw - 32px))");

        PlayerContext context = request.context();
        String target = context.playerName() == null || context.playerName().isBlank()
                ? "this workspace" : context.playerName();
        Span contextLine = new Span("Context: " + target +
                (context.club() == null || context.club().isBlank() ? "" : " · " + context.club()));
        contextLine.addClassName("contextual-assistant-context");
        Paragraph intro = new Paragraph("Ask a focused question without losing your current decision context.");
        intro.addClassName("contextual-assistant-intro");

        VerticalLayout body = new VerticalLayout(contextLine, intro);
        body.setPadding(false);
        body.setSpacing(true);
        body.addClassName("contextual-assistant-body");
        for (String prompt : request.prompts()) {
            Button chip = new Button(prompt, VaadinIcon.ARROW_RIGHT.create(), event -> {
                dialog.close();
                ChatLaunch.open(prompt + contextSuffix(context));
            });
            chip.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            chip.addClassName("contextual-assistant-prompt");
            chip.setWidthFull();
            body.add(chip);
        }

        Button fullChat = new Button("Open full AI workspace", VaadinIcon.CHAT.create(), event -> {
            dialog.close();
            ChatLaunch.open("Help me with " + target + contextSuffix(context));
        });
        fullChat.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button close = new Button("Close", event -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout footer = new HorizontalLayout(close, fullChat);
        footer.setWidthFull();
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        dialog.add(body);
        dialog.getFooter().add(footer);
        dialog.open();
    }

    private static String contextSuffix(PlayerContext context) {
        StringBuilder suffix = new StringBuilder();
        if (context.club() != null && !context.club().isBlank()) {
            suffix.append(" for ").append(context.club());
        }
        if (context.season() != null && !context.season().isBlank()) {
            suffix.append(" in season ").append(context.season());
        }
        return suffix.append(" using the current save data.").toString();
    }
}
