package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.mcp.FmAiAssistentTools;
import com.github.fmaiassistent.mcp.PositionCodes;
import com.github.fmaiassistent.mcp.SquadAdvice;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.github.fmaiassistent.service.PlayerDatabaseService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Route(value = "first-xi", layout = AppShell.class)
@PageTitle("First XI")
@CssImport("./styles/moneyball-view.css")
@CssImport(value = "./styles/player-grid.css", themeFor = "vaadin-grid")
public class FirstXiView extends VerticalLayout {
    private static final String DEFAULT_TACTIC = """
            GK,Ball Playing GK,Sweeper Keeper
            DL,Inverted Full Back,Holding Full Back
            DC,Centre-Back,Centre-Back
            DC,Ball-Playing Centre-Back,Centre-Back
            DR,Inverted Wing-Back,Pressing Full Back
            DMC,Deep Lying Playmaker,Defensive Midfielder
            MC,Midfield Playmaker,Central Midfielder
            MC,Advanced Playmaker,Pressing Central Midfielder
            AML,Wide Forward,Winger
            AMR,Winger,Winger
            ST,Deep Lying Forward,Tracking Centre Forward
            """.stripIndent().trim();

    private final FmAiAssistentTools tools;
    private final ComboBox<String> clubFilter = new ComboBox<>("Club");
    private final TextArea tactic = new TextArea("Tactic slots");
    private final Button runButton = new Button("Pick XI", VaadinIcon.CLIPBOARD_TEXT.create());
    private final Span summary = new Span();
    private final Grid<SquadAdvice.XiPick> grid = new Grid<>();
    private final Grid<UpgradeRow> upgrades = new Grid<>();

    public FirstXiView(FmAiAssistentTools tools, ClubDatabaseService clubs, PlayerDatabaseService players) {
        this.tools = tools;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("moneyball-view");
        addClassName("first-xi-view");
        String liveSlots = String.valueOf(players.metadata().getOrDefault("tactic_slots", ""));
        tactic.setValue(liveSlots.isBlank() ? DEFAULT_TACTIC : liveSlots);
        tactic.setWidthFull();
        tactic.setMinHeight("6em");
        tactic.setMaxHeight("12em");
        String formation = String.valueOf(players.metadata().getOrDefault("tactic_formation", ""));
        tactic.setHelperText("One line per slot: " + String.join(", ", PositionCodes.CODES)
                + " plus in-possession and out-of-possession roles."
                + (formation.isBlank()
                        ? " Load from RAM to fill the live formation."
                        : " Live formation: " + formation + ". Roles still need pasting."));
        configureGrid();
        configureUpgrades();
        Span upgradesHeading = new Span("Suggested buys for holes");
        upgradesHeading.addClassName("first-xi-heading");
        Span recommendedHeading = new Span("Recommended XI (role fit)");
        recommendedHeading.addClassName("first-xi-heading");
        summary.addClassName("moneyball-summary");
        Div boards = new Div(recommendedHeading, grid, upgradesHeading, upgrades);
        boards.addClassName("first-xi-boards");
        add(header(), new LiveSelectedXiPanel(players.metadata()), filterBar(), tactic, summary, boards);
        expand(boards);
        if (clubs.findAllClubs().isEmpty()) {
            grid.setVisible(false);
            upgrades.setVisible(false);
            recommendedHeading.setVisible(false);
            upgradesHeading.setVisible(false);
            summary.setText("Load from RAM on the scouting desk first.");
            summary.addClassName("moneyball-empty");
            return;
        }
        clubFilter.setItems(clubs.findAllClubs().stream()
                .map(club -> club.getName())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList());
    }

    private Component header() {
        Span hint = new Span("Uses the live RAM formation when loaded. Paste roles if you want fit scoring. Same pipeline as fm26_best_xi.");
        hint.addClassName("moneyball-hint");
        hint.setWidthFull();
        return hint;
    }

    private HorizontalLayout filterBar() {
        clubFilter.setPlaceholder("Pick your club");
        clubFilter.setWidth("16em");
        runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        runButton.addClickListener(event -> run());
        HorizontalLayout bar = new HorizontalLayout(clubFilter, runButton);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.END);
        bar.addClassName("moneyball-filters");
        return bar;
    }

    private void configureGrid() {
        grid.addClassName("moneyball-grid");
        grid.addClassName("first-xi-grid");
        grid.setEmptyStateText("Pick a club and run.");
        grid.addColumn(SquadAdvice.XiPick::position).setHeader("Pos").setWidth("4em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.XiPick::playerName).setHeader("Player");
        grid.addColumn(SquadAdvice.XiPick::inPossessionRole).setHeader("In possession");
        grid.addColumn(SquadAdvice.XiPick::outOfPossessionRole).setHeader("Out of possession");
        grid.addColumn(SquadAdvice.XiPick::positionScore).setHeader("Pos score").setWidth("6em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.XiPick::roleFit).setHeader("Role fit").setWidth("6em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.XiPick::ca).setHeader("CA").setWidth("4em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.XiPick::pa).setHeader("PA").setWidth("4em").setFlexGrow(0);
        grid.addColumn(pick -> pick.hole() ? "hole" : "filled")
                .setHeader("Status")
                .setRenderer(new ComponentRenderer<>(pick -> {
                    boolean hole = pick.hole();
                    Span badge = new Span(hole ? "hole" : "filled");
                    badge.addClassName("row-badge");
                    badge.addClassName(hole ? "row-badge-injury" : "row-badge-transfer");
                    return badge;
                }))
                .setWidth("6em")
                .setFlexGrow(0);
    }

    private void configureUpgrades() {
        upgrades.setWidthFull();
        upgrades.addClassName("first-xi-upgrades");
        upgrades.setEmptyStateText("No holes — no suggested buys.");
        upgrades.addColumn(UpgradeRow::position).setHeader("Pos").setWidth("4em").setFlexGrow(0);
        upgrades.addColumn(UpgradeRow::role).setHeader("Role");
        upgrades.addColumn(UpgradeRow::candidates).setHeader("fm26_transfer_shortlist");
    }

    private record UpgradeRow(String position, String role, String candidates) {
    }

    private void run() {
        String club = clubFilter.getValue();
        if (club == null || club.isBlank()) {
            Notification.show("Pick a club first", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        runButton.setEnabled(false);
        try {
            List<SquadAdvice.XiSlot> slots = FmAiAssistentTools.parseTacticSlots(tactic.getValue());
            List<SquadAdvice.XiPick> picks = tools.bestXiRows(club, slots);
            grid.setItems(picks);
            List<String> holes = new ArrayList<>();
            for (SquadAdvice.XiPick pick : picks) {
                if (pick.hole()) {
                    holes.add(pick.position());
                }
            }
            List<UpgradeRow> rows = new ArrayList<>();
            for (Map<String, Object> buy : tools.suggestedBuys(club, picks)) {
                Object raw = buy.get("candidates");
                String names = "";
                if (raw instanceof List<?> list) {
                    names = list.stream()
                            .map(item -> item instanceof Map<?, ?> map ? String.valueOf(map.get("name")) : "")
                            .filter(name -> !name.isBlank())
                            .reduce((left, right) -> left + ", " + right)
                            .orElse("");
                }
                rows.add(new UpgradeRow(
                        String.valueOf(buy.get("position")),
                        String.valueOf(buy.get("in_possession_role")),
                        names));
            }
            upgrades.setItems(rows);
            summary.setText(holes.isEmpty() ? "XI filled" : "Holes: " + String.join(", ", holes)
                    + " — suggested buys from fm26_transfer_shortlist");
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        } finally {
            runButton.setEnabled(true);
        }
    }
}
