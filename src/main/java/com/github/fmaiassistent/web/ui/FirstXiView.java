package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import com.github.fmaiassistent.mcp.FmAiAssistentTools;
import com.github.fmaiassistent.mcp.PositionCodes;
import com.github.fmaiassistent.mcp.SquadAdvice;
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

    private final FmAiAssistentTools tools;
    private final String sessionClub;
    private final MoneyCurrency currency;
    private final Map<String, Object> metadata;
    private final TextArea tactic = new TextArea("Tactic slots");
    private final Button runButton = new Button("Pick XI", VaadinIcon.CLIPBOARD_TEXT.create());
    private final Span summary = new Span();
    private final PitchBoard pitch = new PitchBoard();
    private final Div unavailable = new Div();
    private final Grid<SquadAdvice.XiPick> grid = new Grid<>();
    private final Grid<UpgradeRow> upgrades = new Grid<>();

    public FirstXiView(
            FmAiAssistentTools tools,
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
        String liveSlots = String.valueOf(metadata.getOrDefault("tactic_slots", ""));
        tactic.setValue(liveSlots.isBlank() ? DEFAULT_TACTIC : liveSlots);
        tactic.setWidthFull();
        tactic.setMinHeight("6em");
        tactic.setMaxHeight("12em");
        String formation = String.valueOf(metadata.getOrDefault("tactic_formation", ""));
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
        Div boards = new Div(recommendedHeading, grid, upgradesHeading, upgrades);
        boards.addClassName("first-xi-boards");
        add(header(), new LiveSelectedXiPanel(metadata), filterBar(), tactic, summary,
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
        Span hint = new Span("Live RAM XI on the pitch — injured greyscale with return dates. Injured players are left out of the recommended XI. Paste roles if you want fit scoring.");
        hint.addClassName("moneyball-hint");
        hint.setWidthFull();
        return hint;
    }

    private HorizontalLayout filterBar() {
        runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        runButton.addClickListener(event -> run());
        HorizontalLayout bar = new HorizontalLayout(runButton);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.END);
        bar.addClassName("moneyball-filters");
        return bar;
    }

    private void configureGrid() {
        grid.addClassName("moneyball-grid");
        grid.addClassName("first-xi-grid");
        grid.setEmptyStateText("Pick your club in the top bar and run.");
        grid.addItemClickListener(event -> {
            SquadAdvice.XiPick pick = event.getItem();
            if (pick.hole()) {
                ChatLaunch.open(ChatLaunch.explainHole(pick.position(), sessionClub));
                return;
            }
            PlayerDossier.openNamed(tools, pick.playerName(), currency, sessionClub);
        });
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

    private record RunResult(
            List<SquadAdvice.XiPick> picks,
            List<Map<String, Object>> unavailable,
            List<Map<String, Object>> buys) {
    }

    private void run() {
        if (sessionClub.isBlank()) {
            Notification.show("Pick your club in the top bar", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        UI ui = UI.getCurrent();
        runButton.setEnabled(false);
        CompletableFuture.supplyAsync(() -> {
            List<SquadAdvice.XiSlot> slots = FmAiAssistentTools.parseTacticSlots(tactic.getValue());
            List<SquadAdvice.XiPick> picks = tools.bestXiRows(sessionClub, slots);
            return new RunResult(picks, tools.unavailableForClub(sessionClub), tools.suggestedBuys(sessionClub, picks));
        }).thenAccept(result -> ui.access(() -> {
            List<SquadAdvice.XiPick> picks = result.picks();
            List<Map<String, Object>> out = result.unavailable();
            List<Map<String, Object>> buys = result.buys();
            grid.setItems(picks);
            renderUnavailable(out);
            showPitch(picks, out);
            List<String> holes = new ArrayList<>();
            for (SquadAdvice.XiPick pick : picks) {
                if (pick.hole()) {
                    holes.add(pick.position());
                }
            }
            List<UpgradeRow> rows = new ArrayList<>();
            for (Map<String, Object> buy : buys) {
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
            summary.removeClassName("moneyball-empty");
            summary.setText(holes.isEmpty() ? "XI filled" : "Holes: " + String.join(", ", holes)
                    + " — suggested buys from fm26_transfer_shortlist");
            runButton.setEnabled(true);
        })).exceptionally(ex -> {
            ui.access(() -> {
                Notification.show(ex.getCause() instanceof RuntimeException re ? re.getMessage() : ex.getMessage(),
                        5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                runButton.setEnabled(true);
            });
            return null;
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
                .map(slot -> new SquadAdvice.XiPick(
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

    private void showPitch(List<SquadAdvice.XiPick> recommended, List<Map<String, Object>> unavailableRows) {
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
}
