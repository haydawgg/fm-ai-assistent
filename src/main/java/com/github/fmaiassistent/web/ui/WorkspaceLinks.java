package com.github.fmaiassistent.web.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

final class WorkspaceLinks {
    private WorkspaceLinks() {
    }

    static HorizontalLayout buttons() {
        HorizontalLayout links = new HorizontalLayout(
                nav("Desk", VaadinIcon.GRID.create(), ""),
                nav("Shortlist", VaadinIcon.SEARCH.create(), "shortlist"),
                nav("Moneyball", VaadinIcon.TRENDING_UP.create(), "moneyball"),
                nav("Squad trim", VaadinIcon.MINUS.create(), "squad-trim"),
                nav("First XI", VaadinIcon.CLIPBOARD_TEXT.create(), "first-xi"),
                nav("Compare", VaadinIcon.SPLIT.create(), "compare-squads"),
                nav("Chat", VaadinIcon.CHAT.create(), "chat"));
        links.setSpacing(true);
        links.setAlignItems(FlexComponent.Alignment.CENTER);
        return links;
    }

    private static Button nav(String label, Icon icon, String route) {
        Button button = new Button(label, icon, event -> event.getSource().getUI().ifPresent(ui -> ui.navigate(route)));
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        return button;
    }
}
