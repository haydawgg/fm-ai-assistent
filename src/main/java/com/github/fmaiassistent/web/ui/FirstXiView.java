package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import com.github.fmaiassistent.football.FirstXiPick;
import com.github.fmaiassistent.football.FirstXiSlot;
import com.github.fmaiassistent.football.FirstXiSuggestionQuery;
import com.github.fmaiassistent.football.PlayerAnalysisPort;
import com.github.fmaiassistent.mcp.FmAiAssistentTools;
import com.github.fmaiassistent.mcp.PositionCodes;
import com.github.fmaiassistent.service.AppSettingsService;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.github.fmaiassistent.service.PlayerDatabaseService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Route(value = "first-xi", layout = AppShell.class)
@PageTitle("First XI")
@CssImport("./styles/moneyball-view.css")
@CssImport("./styles/pitch-board.css")
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

    private final PlayerAnalysisPort tools;
    private final String sessionClub;
    private final MoneyCurrency currency;
    private final Map<String, Object> metadata;
    private final TextArea tactic = new TextArea();
    private final Button runButton = new Button("Pick XI", VaadinIcon.CLIPBOARD_TEXT.create());
    private final Span summary = new Span();
    private final PitchBoard pitch = new PitchBoard();
    private final Div unavailable = new Div();
    private final Grid<FirstXiPick> grid = new Grid<>();
    private final Grid<UpgradeRow> upgrades = new Grid<>();

    public FirstXiView(
            PlayerAnalysisPort tools,
            ClubDatabaseService clubs,
            PlayerDatabaseService players,
            AppSettingsService settings) {
        this.tools = tools;
        this.metadata = players.metadata();
        this.sessionClub = SessionClub.resolved(settings, SessionClub.names(clubs));
        this.currency = settings.currency();
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("moneyball-view");
        addClassName("first-xi-view");
        String liveSlots = display(metadata.get("tactic_slots"));
        tactic.setValue(liveSlots.isBlank() ? DEFAULT_TACTIC : liveSlots);
        tactic.setWidthFull();
        tactic.setMinHeight("6em");
        tactic.setMaxHeight("12em");
        tactic.setAriaLabel("Tactical role blueprint");
        String formation = display(metadata.get("tactic_formation"));
        tactic.setHelperText("One line per slot: " + String.join(", ", PositionCodes.CODES)
                + " plus in-possession and out-of-possession roles."
                + (formation.isBlank()
                        ? " Load from the top bar to fill the live formation."
                        : " Live formation: " + formation + ". Roles still need pasting."));
        configureGrid();
        configureUpgrades();
        unavailable.addClassName("unavailable-strip");
        Span upgradesHeading = new Span("Suggested buys for holes");
        upgradesHeading.addClassName("first-xi-heading");
        Span recommendedHeading = new Span("Recommended XI (role fit)");
        recommendedHeading.addClassName("first-xi-heading");
        Span unavailableHeading = new Span("Unavailable");
        unavailableHeading.addClassName("first-xi-heading");
        summary.addClassName("moneyball-summary");
        summary.getElement().setAttribute("role", "status");
        summary.getElement().setAttribute("aria-live", "polite");
        Div boards = new Div(recommendedHeading, grid, upgradesHeading, upgrades);
        boards.addClassName("first-xi-boards");
        add(header(), workflowSteps(), new LiveSelectedXiPanel(metadata), filterBar(), tacticEditor(), summary,
                pitch, unavailableHeading, unavailable, boards);
        expand(boards);
        if (!clubs.hasClubs()) {
            grid.setVisible(false);
            upgrades.setVisible(false);
            recommendedHeading.setVisible(false);
            upgradesHeading.setVisible(false);
            summary.setText("Load from the top bar with FM26 running.");
            summary.addClassName("moneyball-empty");
            return;
        }
        if (sessionClub.isBlank()) {
            summary.setText("Pick your club in the top bar.");
            summary.addClassName("moneyball-empty");
            showLivePitch(Map.of());
            return;
        }
        run();
    }

    private Component header() {
        return new WorkspaceHeader("First XI",
                "Live RAM XI on the pitch, with unavailable players separated from the recommended role-fit selection.");
    }

    private Component workflowSteps() {
        Div steps = new Div();
        steps.addClassName("first-xi-workflow");
        String[] labels = {"1 · Tactic", "2 · Eligibility", "3 · Select XI", "4 · Review gaps"};
        for (String label : labels) {
            Span step = new Span(label);
            step.addClassName("first-xi-workflow-step");
            steps.add(step);
        }
        return steps;
    }

    private HorizontalLayout filterBar() {
        runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        runButton.setTooltipText("Recalculate the recommended XI from this role blueprint");
        runButton.addClickListener(event -> run());
        Button ask = new Button("Ask FM AI", VaadinIcon.CHAT.create(), event ->
                ContextualAssistantPanel.open(new ContextualAssistantRequest(
                        new PlayerContext("First XI", sessionClub,
                                display(metadata.get("season_key")), display(metadata.get("season_stats_read_at"))),
                        List.of("Suggest changes to this XI.", "Which positions need attention?", "Explain the role-fit gaps."))));
        ask.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout bar = new HorizontalLayout(runButton, ask);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.END);
        bar.addClassName("moneyball-filters");
        return bar;
    }

    private Component tacticEditor() {
        Div panel = new Div();
        panel.addClassName("first-xi-tactic-panel");

        Span eyebrow = new Span("Tactical input");
        eyebrow.addClassName("first-xi-panel-eyebrow");
        Span title = new Span("Role blueprint");
        title.addClassName("first-xi-panel-title");
        panel.add(eyebrow, title, tactic);
        return panel;
    }

    private void configureGrid() {
        grid.addClassName("moneyball-grid");
        grid.addClassName("first-xi-grid");
        grid.setEmptyStateText("Pick your club in the top bar and run.");
        grid.addItemClickListener(event -> {
            if (event.getColumn() != null && "ask".equals(event.getColumn().getKey())) {
                return;
            }
            FirstXiPick pick = event.getItem();
            if (pick.hole()) {
                ChatLaunch.open(ChatLaunch.explainHole(pick.position(), sessionClub));
                return;
            }
            PlayerDossier.openNamed(tools, pick.playerName(), currency, sessionClub);
        });
        grid.addComponentColumn(pick -> {
            if (pick.hole()) {
                Button button = new Button(VaadinIcon.CHAT.create());
                button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
                button.setTooltipText("Ask FM AI about this hole");
                button.getElement().setAttribute("aria-label", "Ask FM AI about this hole");
                button.addClickListener(event -> ChatLaunch.open(ChatLaunch.explainHole(pick.position(), sessionClub)));
                return button;
            }
            return ChatLaunch.askButton(pick.playerName(), sessionClub);
        }).setHeader("").setKey("ask").setWidth("3.5em").setFlexGrow(0);
        grid.addColumn(FirstXiPick::position).setHeader("Pos").setWidth("4em").setFlexGrow(0);
        grid.addColumn(FirstXiPick::playerName).setHeader("Player");
        grid.addColumn(FirstXiPick::inPossessionRole).setHeader("In possession");
        grid.addColumn(FirstXiPick::outOfPossessionRole).setHeader("Out of possession");
        grid.addColumn(FirstXiPick::positionScore).setHeader("Pos score").setWidth("6em").setFlexGrow(0);
        grid.addColumn(FirstXiPick::roleFit).setHeader("Role fit").setWidth("6em").setFlexGrow(0);
        grid.addColumn(FirstXiPick::ca).setHeader("CA").setWidth("4em").setFlexGrow(0);
        grid.addColumn(FirstXiPick::pa).setHeader("PA").setWidth("4em").setFlexGrow(0);
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

    private record RunResult(
            List<FirstXiPick> picks,
            List<Map<String, Object>> unavailable,
            List<Map<String, Object>> buys) {
    }

    private void run() {
        if (sessionClub.isBlank()) {
            UiFeedback.show("Pick your club in the top bar", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        UI ui = UI.getCurrent();
        runButton.setEnabled(false);
        summary.removeClassName("moneyball-empty");
        summary.setText("Evaluating role fit…");
        summary.getElement().setAttribute("aria-busy", "true");
        String tacticText = tactic.getValue();
        UiAsync.submit(ui, () -> {
            List<FirstXiSlot> slots = FmAiAssistentTools.parseTacticSlots(tacticText).stream()
                    .map(slot -> new FirstXiSlot(slot.position(), slot.inPossessionRole(), slot.outOfPossessionRole()))
                    .toList();
            List<FirstXiPick> picks = tools.bestXi(sessionClub, slots);
            return new RunResult(picks, tools.unavailableForClub(sessionClub),
                    tools.suggestedBuys(new FirstXiSuggestionQuery(sessionClub, picks)));
        }, result -> {
            List<FirstXiPick> picks = result.picks();
            List<Map<String, Object>> out = result.unavailable();
            List<Map<String, Object>> buys = result.buys();
            grid.setItems(picks);
            renderUnavailable(out);
            showPitch(picks, out);
            List<String> holes = new ArrayList<>();
            for (FirstXiPick pick : picks) {
                if (pick.hole()) {
                    holes.add(pick.position());
                }
            }
            List<UpgradeRow> rows = new ArrayList<>();
            for (Map<String, Object> buy : buys) {
                Object raw = buy.get("candidates");
                List<String> candidateNames = new ArrayList<>();
                if (raw instanceof List<?> list) {
                    for (Object item : list) {
                        if (!(item instanceof Map<?, ?> map)) {
                            continue;
                        }
                        String name = display(map.get("name"));
                        if (!name.isBlank()) {
                            candidateNames.add(name);
                        }
                    }
                }
                rows.add(new UpgradeRow(
                        display(buy.get("position")),
                        display(buy.get("in_possession_role")),
                        String.join(", ", candidateNames)));
            }
            upgrades.setItems(rows);
            summary.removeClassName("moneyball-empty");
            summary.setText(holes.isEmpty() ? "XI filled" : "Holes: " + String.join(", ", holes)
                    + " — suggested buys from fm26_transfer_shortlist");
            summary.getElement().setAttribute("aria-busy", "false");
            runButton.setEnabled(true);
        }, ex -> {
            UiFeedback.error(ex, "Evaluation failed — adjust the role blueprint and try again.");
            summary.setText("Evaluation failed — adjust the role blueprint and try again.");
            summary.getElement().setAttribute("aria-busy", "false");
            runButton.setEnabled(true);
        });
    }

    private void showLivePitch(Map<String, String> injuryNotes) {
        List<LiveSelectedXiPanel.SelectedSlot> live = LiveSelectedXiPanel.parse(
                stringMeta(metadata, "tactic_selected"));
        if (live.isEmpty()) {
            pitch.setEmpty();
            return;
        }
        pitch.show(live.stream()
                .map(slot -> new FirstXiPick(
                        slot.position(),
                        "",
                        "",
                        slot.playerName(),
                        0,
                        null,
                        0,
                        0,
                        slot.playerName() == null || slot.playerName().isBlank()))
                .toList(), injuryNotes);
    }

    private void showPitch(List<FirstXiPick> recommended, List<Map<String, Object>> unavailableRows) {
        Map<String, String> notes = injuryNotes(unavailableRows);
        List<LiveSelectedXiPanel.SelectedSlot> live = LiveSelectedXiPanel.parse(
                stringMeta(metadata, "tactic_selected"));
        if (!live.isEmpty()) {
            showLivePitch(notes);
            return;
        }
        pitch.show(recommended, notes);
    }

    private void renderUnavailable(List<Map<String, Object>> rows) {
        unavailable.removeAll();
        if (rows == null || rows.isEmpty()) {
            Span empty = new Span("Nobody currently injured in this squad.");
            empty.addClassName("moneyball-summary");
            unavailable.add(empty);
            return;
        }
        for (Map<String, Object> row : rows) {
            Div chip = new Div();
            chip.addClassName("unavailable-chip");
            Span name = new Span(String.valueOf(row.getOrDefault("name", "")));
            name.addClassName("unavailable-chip-name");
            Span meta = new Span(unavailableMeta(row));
            meta.addClassName("unavailable-chip-meta");
            chip.add(name, meta);
            unavailable.add(chip);
        }
    }

    private static Map<String, String> injuryNotes(List<Map<String, Object>> rows) {
        Map<String, String> notes = new LinkedHashMap<>();
        if (rows == null) {
            return notes;
        }
        for (Map<String, Object> row : rows) {
            Object name = row.get("name");
            if (name == null || String.valueOf(name).isBlank()) {
                continue;
            }
            notes.put(String.valueOf(name).strip().toLowerCase(Locale.ROOT), returnText(row));
        }
        return notes;
    }

    private static String unavailableMeta(Map<String, Object> row) {
        Object position = row.get("position");
        String pos = position == null ? "" : String.valueOf(position);
        String ret = returnText(row);
        if (pos.isBlank()) {
            return ret;
        }
        return ret.isBlank() ? pos : pos + " · " + ret;
    }

    private static String returnText(Map<String, Object> row) {
        Object min = row.get("min_days_remaining");
        Object max = row.get("max_days_remaining");
        if (min instanceof Number minNumber) {
            int days = minNumber.intValue();
            if (max instanceof Number maxNumber && maxNumber.intValue() != days) {
                return "back in " + days + "–" + maxNumber.intValue() + " days";
            }
            return "back in " + days + " days";
        }
        Object expected = row.get("expected_return");
        if (expected != null && !String.valueOf(expected).isBlank()) {
            return "back " + expected;
        }
        Object injury = row.get("injury");
        return injury == null ? "injured" : String.valueOf(injury);
    }

    private static String stringMeta(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return "";
        }
        Object value = metadata.get(key);
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        return "null".equals(text) ? "" : text;
    }

    private static String display(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        return "null".equals(text) ? "" : text;
    }
}
