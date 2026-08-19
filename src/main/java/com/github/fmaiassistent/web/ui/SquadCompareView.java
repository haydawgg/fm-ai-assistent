package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import com.github.fmaiassistent.mcp.FmAiAssistentTools;
import com.github.fmaiassistent.mcp.SquadAdvice;
import com.github.fmaiassistent.service.AppSettingsService;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Route(value = "compare-squads", layout = AppShell.class)
@PageTitle("Compare squads")
@CssImport("./styles/moneyball-view.css")
@CssImport(value = "./styles/player-grid.css", themeFor = "vaadin-grid")
public class SquadCompareView extends VerticalLayout {
    private final FmAiAssistentTools tools;
    private final ComboBox<String> leftClub = new ComboBox<>("Your club");
    private final ComboBox<String> rightClub = new ComboBox<>("Other club");
    private final Button runButton = new Button("Compare", VaadinIcon.SPLIT.create());
    private final Span summary = new Span();
    private final Grid<SquadAdvice.SquadCompareRow> grid = new Grid<>();
    private final MoneyCurrency currency;

    public SquadCompareView(FmAiAssistentTools tools, ClubDatabaseService clubs, AppSettingsService settings) {
        this.tools = tools;
        this.currency = settings.currency();
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("moneyball-view");
        configureGrid();
        add(header(), filterBar(), summary, grid);
        expand(grid);
        summary.addClassName("moneyball-summary");
        summary.getElement().setAttribute("role", "status");
        summary.getElement().setAttribute("aria-live", "polite");
        if (!clubs.hasClubs()) {
            grid.setVisible(false);
            summary.setText("Load from the top bar with FM26 running.");
            summary.addClassName("moneyball-empty");
            return;
        }
        List<String> names = SessionClub.names(clubs);
        leftClub.setItems(names);
        rightClub.setItems(names);
        String session = SessionClub.resolved(settings, names);
        if (!session.isBlank()) {
            leftClub.setValue(session);
            leftClub.setReadOnly(true);
            leftClub.setHelperText("Your club from the top bar");
        }
    }

    private Component header() {
        return new WorkspaceHeader("Compare squads",
                "Compare the best player per position by CA, with the same numbers as fm26_compare_squads.");
    }

    private HorizontalLayout filterBar() {
        leftClub.setPlaceholder("Pick your club in the top bar");
        rightClub.setPlaceholder("Pick a club");
        leftClub.setWidth("16em");
        rightClub.setWidth("16em");
        runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        runButton.addClickListener(event -> run());
        HorizontalLayout bar = new HorizontalLayout(leftClub, rightClub, runButton);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.END);
        bar.addClassName("moneyball-filters");
        return bar;
    }

    private void configureGrid() {
        grid.addClassName("moneyball-grid");
        grid.setEmptyStateText("Pick two clubs to compare.");
        grid.addColumn(SquadAdvice.SquadCompareRow::position).setHeader("Pos").setWidth("4em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.SquadCompareRow::leftName).setHeader("Left player");
        grid.addColumn(SquadAdvice.SquadCompareRow::leftCa).setHeader("Left CA").setWidth("6em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.SquadCompareRow::rightName).setHeader("Right player");
        grid.addColumn(SquadAdvice.SquadCompareRow::rightCa).setHeader("Right CA").setWidth("6em").setFlexGrow(0);
        grid.addColumn(row -> row.caGap() > 0 ? "+" + row.caGap() : String.valueOf(row.caGap()))
                .setHeader("CA gap")
                .setRenderer(new ComponentRenderer<>(row -> {
                    int gap = row.caGap();
                    Span value = new Span(gap > 0 ? "+" + gap : String.valueOf(gap));
                    value.addClassName(gap >= 0 ? "gap-positive" : "gap-negative");
                    return value;
                }))
                .setWidth("6em")
                .setFlexGrow(0);
    }

    private void run() {
        String left = leftClub.getValue();
        String right = rightClub.getValue();
        if (left == null || left.isBlank() || right == null || right.isBlank()) {
            Notification.show("Pick your club in the top bar and another club", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        UI ui = UI.getCurrent();
        runButton.setEnabled(false);
        summary.removeClassName("moneyball-empty");
        summary.setText("Comparing squads…");
        summary.getElement().setAttribute("aria-busy", "true");
        CompletableFuture.supplyAsync(() -> tools.compareSquads(left, right))
                .thenAccept(result -> OpenRouterModelPicker.access(ui, () -> {
                    @SuppressWarnings("unchecked")
                    List<SquadAdvice.SquadCompareRow> rows = (List<SquadAdvice.SquadCompareRow>) result.get("positions");
                    grid.setItems(rows == null ? List.of() : rows);
                    summary.setText(cardText("Left", result.get("left")) + "  ·  " + cardText("Right", result.get("right")));
                    summary.getElement().setAttribute("aria-busy", "false");
                    runButton.setEnabled(true);
                })).exceptionally(ex -> {
                    OpenRouterModelPicker.access(ui, () -> {
                        Notification.show(ex.getCause() instanceof RuntimeException re ? re.getMessage() : ex.getMessage(),
                                5000, Notification.Position.MIDDLE)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                        summary.setText("Squad comparison failed — choose two clubs and try again.");
                        summary.getElement().setAttribute("aria-busy", "false");
                        runButton.setEnabled(true);
                    });
                    return null;
                });
    }

    private String cardText(String label, Object raw) {
        if (!(raw instanceof Map<?, ?> card)) {
            return label;
        }
        return label + " " + card.get("club")
                + ": " + card.get("players") + " players, avg CA " + card.get("average_ca")
                + ", wage/wk " + MoneyDisplay.format(asLong(card.get("wage_bill_weekly")), currency);
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
