package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.mcp.FmAiAssistentTools;
import com.github.fmaiassistent.mcp.PositionCodes;
import com.github.fmaiassistent.mcp.SquadAdvice;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.vaadin.flow.component.Component;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Route("first-xi")
@PageTitle("First XI")
@CssImport("./styles/moneyball-view.css")
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

    public FirstXiView(FmAiAssistentTools tools, ClubDatabaseService clubs) {
        this.tools = tools;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        addClassName("moneyball-view");
        tactic.setValue(DEFAULT_TACTIC);
        tactic.setWidthFull();
        tactic.setMinHeight("12em");
        tactic.setHelperText("One line per slot: " + String.join(", ", PositionCodes.CODES)
                + " plus in-possession and out-of-possession roles. Current tactic is not read from RAM.");
        configureGrid();
        configureUpgrades();
        add(header(), filterBar(), tactic, summary, grid, new Span("Suggested buys for holes"), upgrades);
        expand(grid);
        if (clubs.findAllClubs().isEmpty()) {
            grid.setVisible(false);
            summary.setText("Load from RAM on the scouting desk first.");
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
        Span title = new Span("First XI");
        title.addClassName("moneyball-title");
        Span hint = new Span("Paste a tactic. Best available squad player is assigned per slot. Same pipeline as fm26_best_xi.");
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

    private HorizontalLayout filterBar() {
        clubFilter.setPlaceholder("Pick your club");
        clubFilter.setWidth("16em");
        runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        runButton.addClickListener(event -> run());
        HorizontalLayout bar = new HorizontalLayout(clubFilter, runButton);
        bar.setAlignItems(FlexComponent.Alignment.END);
        bar.addClassName("moneyball-filters");
        return bar;
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.setEmptyStateText("Pick a club and run.");
        grid.addColumn(SquadAdvice.XiPick::position).setHeader("Pos").setWidth("4em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.XiPick::playerName).setHeader("Player");
        grid.addColumn(SquadAdvice.XiPick::inPossessionRole).setHeader("In possession");
        grid.addColumn(SquadAdvice.XiPick::outOfPossessionRole).setHeader("Out of possession");
        grid.addColumn(SquadAdvice.XiPick::positionScore).setHeader("Pos score").setWidth("6em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.XiPick::roleFit).setHeader("Role fit").setWidth("6em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.XiPick::ca).setHeader("CA").setWidth("4em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.XiPick::pa).setHeader("PA").setWidth("4em").setFlexGrow(0);
        grid.addColumn(pick -> pick.hole() ? "hole" : "filled").setHeader("Status").setWidth("6em").setFlexGrow(0);
    }

    private void configureUpgrades() {
        upgrades.setWidthFull();
        upgrades.setMaxHeight("16em");
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
        }
    }
}
