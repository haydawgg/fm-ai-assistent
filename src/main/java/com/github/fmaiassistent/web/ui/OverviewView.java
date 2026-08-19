package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.mcp.FmAiAssistentTools;
import com.github.fmaiassistent.mcp.SquadAdvice;
import com.github.fmaiassistent.service.AppSettingsService;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.github.fmaiassistent.service.DemoDataService;
import com.github.fmaiassistent.service.RamLoadCoordinator;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Route(value = "", layout = AppShell.class)
@PageTitle("Dashboard")
@CssImport("./styles/overview-view.css")
public class OverviewView extends VerticalLayout {
    private final DashboardSnapshotService dashboard;
    private final FmAiAssistentTools tools;
    private final ClubDatabaseService clubs;
    private final AppSettingsService settings;
    private final RamLoadCoordinator ramLoad;
    private final DemoDataService demoData;
    private DashboardSnapshot snapshot;

    public OverviewView(
            DashboardSnapshotService dashboard,
            FmAiAssistentTools tools,
            ClubDatabaseService clubs,
            AppSettingsService settings,
            RamLoadCoordinator ramLoad,
            DemoDataService demoData) {
        this.dashboard = dashboard;
        this.tools = tools;
        this.clubs = clubs;
        this.settings = settings;
        this.ramLoad = ramLoad;
        this.demoData = demoData;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("overview-view");
        renderLoading();
        refresh();
    }

    private void refresh() {
        String club = SessionClub.resolved(settings, SessionClub.names(clubs));
        UI ui = UI.getCurrent();
        CompletableFuture.supplyAsync(() -> dashboard.load(club))
                .thenAccept(result -> {
                    if (ui == null || !ui.isAttached()) {
                        return;
                    }
                    ui.access(() -> {
                        snapshot = result;
                        render();
                    });
                })
                .exceptionally(error -> {
                    if (ui != null && ui.isAttached()) {
                        ui.access(() -> renderError(error));
                    }
                    return null;
                });
    }

    private void render() {
        removeAll();
        if (snapshot == null) {
            renderLoading();
            return;
        }
        if (snapshot.heartbeat().empty()) {
            add(emptySnapshot());
            return;
        }
        if (!snapshot.clubAvailable()) {
            add(emptyClubState());
            return;
        }
        add(commandHeader(), dataConfidenceBanner(), metrics(), middleRow(), lowerRow(), footerNote());
    }

    private Component commandHeader() {
        String club = snapshot.clubName().isBlank() ? "Club command center" : snapshot.clubName() + " command center";
        Div copy = new Div();
        Span eyebrow = new Span("FM AI ASSISTENT / OPERATIONS");
        eyebrow.addClassName("overview-eyebrow");
        H2 title = new H2(club);
        Paragraph subtitle = new Paragraph(demoData.enabled()
                ? "Demo data is for visual preview only. Load a live FM26 snapshot before making decisions."
                : snapshot.heartbeat().stale()
                        ? "Snapshot is stale. Refresh before making transfer or squad decisions."
                        : "The decisions that matter, assembled before you open a specialist workspace.");
        copy.add(eyebrow, title, subtitle);

        Button refresh = new Button("Refresh board", VaadinIcon.REFRESH.create(), event -> refresh());
        refresh.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button load = new Button(snapshot.heartbeat().stale() ? "Load latest" : "Load from RAM", VaadinIcon.DATABASE.create());
        load.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        load.addClickListener(event -> RamLoadUi.start(ramLoad, load));
        Button ask = new Button("Ask FM AI", VaadinIcon.CHAT.create(), event ->
                ChatLaunch.open("What should I focus on for my club right now?"));
        ask.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout actions = new HorizontalLayout(statusPill(), refresh, load, ask);
        actions.addClassName("overview-header-actions");
        Div header = new Div(copy, actions);
        header.addClassName("overview-header");
        return header;
    }

    private Component statusPill() {
        Span status = new Span(demoData.enabled()
                ? "DEMO DATA"
                : snapshot.heartbeat().stale() ? "STALE SNAPSHOT" : "LIVE SNAPSHOT");
        status.addClassName("overview-status");
        status.addClassName(demoData.enabled() || snapshot.heartbeat().stale()
                ? "overview-status-warning" : "overview-status-ready");
        status.getElement().setAttribute("title", snapshot.heartbeat().title());
        return status;
    }

    private Component dataConfidenceBanner() {
        boolean demo = demoData.enabled();
        boolean stale = snapshot.heartbeat().stale();
        String title;
        String detail;
        String tone;
        if (demo) {
            title = "Demo snapshot";
            detail = "This board is populated with preview data. Load from RAM to work with your current save.";
            tone = "warning";
        } else if (stale) {
            title = "Stale snapshot";
            detail = "The save may have moved on. Reload FM26 data before acting on transfer or squad recommendations.";
            tone = "warning";
        } else if (snapshot.partial()) {
            title = "Partial decision data";
            detail = "Some panels need more source data. Unknown values below explain what is missing.";
            tone = "info";
        } else {
            title = "Decision data ready";
            detail = "This snapshot is current enough to review the recommended actions below.";
            tone = "ready";
        }

        Div banner = new Div();
        banner.addClassName("overview-data-confidence");
        banner.addClassName("overview-confidence-" + tone);
        banner.getElement().setAttribute("role", "status");
        Span label = new Span(title);
        label.addClassName("overview-confidence-title");
        Span copy = new Span(detail);
        copy.addClassName("overview-confidence-detail");
        banner.add(label, copy);
        return banner;
    }

    private Component metrics() {
        DashboardSnapshot.Metrics m = snapshot.metrics();
        DashboardSnapshot.Tactical tactical = snapshot.tactical();
        Div cards = new Div(
                metric("Squad overview", m.squadCount() + " players", m.injured() + " unavailable", "accent", VaadinIcon.USERS),
                metric("Team ability", m.averageCa() == null ? "Unknown" : "CA " + m.averageCa(),
                        m.averageCa() == null ? "CA unavailable for this squad" : "Current ability average", "info", VaadinIcon.STAR),
                metric("Transfer budget", money(m.transferBudget()), "Available club budget", "warning", VaadinIcon.MONEY),
                metric("Squad value", money(m.squadValue()), m.knownValuations() == 0
                        ? "No asking-price data in snapshot"
                        : m.knownValuations() + " asking prices included", "value", VaadinIcon.TRENDING_UP),
                metric("First XI strength", tactical.firstXiStrength() == null ? "Unknown" : "CA " + tactical.firstXiStrength(),
                        tactical.firstXiStrength() == null ? "Requires a filled XI from the current tactic" : tactical.picks().size() + " tactical slots evaluated",
                        "info", VaadinIcon.CLIPBOARD_TEXT),
                metric("Tactical fit", tactical.tacticalFit() == null ? "Unknown" : tactical.tacticalFit() + "%",
                        !tacticConfigured() ? "Add role assignments to score fit" : tactical.tacticalFit() == null ? "Role-fit scores unavailable" : tactical.holes() + " positional holes",
                        tactical.holes() > 0 ? "danger" : "accent", VaadinIcon.COG));
        cards.addClassName("overview-metrics");
        return cards;
    }

    private Component middleRow() {
        Div row = new Div(recommendedActions(), tacticalOverview(), aiPreview());
        row.addClassName("overview-grid");
        row.addClassName("overview-grid-middle");
        return row;
    }

    private Component lowerRow() {
        Div row = new Div(shortlist(), depth(), trim());
        row.addClassName("overview-grid");
        row.addClassName("overview-grid-lower");
        return row;
    }

    private Component recommendedActions() {
        Div body = new Div();
        body.addClassName("overview-action-list");
        for (DashboardSnapshot.Action action : snapshot.actions()) {
            Div row = new Div();
            row.addClassName("overview-recommended-action");
            row.addClassName("overview-tone-" + action.tone());
            row.getElement().setAttribute("role", "button");
            row.getElement().setAttribute("tabindex", "0");
            row.getElement().setAttribute("aria-label", action.title() + ". " + action.detail());
            row.getElement().executeJs("this.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); this.click(); } });");
            row.addClickListener(event -> getUI().ifPresent(ui -> ui.navigate(action.route())));
            row.add(iconFor(action.route()).create());
            Div text = new Div();
            Span title = new Span(action.title());
            title.addClassName("overview-action-title");
            Span detail = new Span(action.detail());
            detail.addClassName("overview-action-detail");
            text.add(title, detail);
            Span count = new Span(action.count());
            count.addClassName("overview-action-count");
            row.add(text, count);
            row.getElement().setAttribute("title", action.detail());
            body.add(row);
        }
        return panel("Recommended actions", "The next decisions from your live snapshot.", body,
                "View squad trim", "squad-trim");
    }

    private Component tacticalOverview() {
        Div body = new Div();
        body.addClassName("overview-tactical-body");
        Div meta = new Div();
        meta.addClassName("overview-tactical-meta");
        boolean tacticConfigured = tacticConfigured();
        Span formation = new Span(tacticConfigured ? snapshot.tactical().formation() : "No tactic loaded");
        formation.addClassName("overview-formation");
        Span holes = new Span(tacticConfigured
                ? snapshot.tactical().holes() + " holes"
                : snapshot.tactical().picks().size() + " roles need data");
        holes.addClassName(!tacticConfigured ? "overview-badge-warning"
                : snapshot.tactical().holes() > 0 ? "overview-badge-danger" : "overview-badge-success");
        meta.add(formation, holes);
        PitchBoard pitch = new PitchBoard();
        pitch.show(snapshot.tactical().picks());
        pitch.addClassName("overview-pitch");
        Span summary = new Span(!tacticConfigured
                ? "Load or paste a tactic to evaluate role fit."
                : snapshot.tactical().unavailable().isEmpty()
                        ? "Role fit from the live squad."
                        : snapshot.tactical().unavailable().size() + " unavailable player(s) separated from the XI.");
        summary.addClassName("overview-panel-note");
        body.add(meta, pitch, summary);
        return panel("Tactical overview", "Live First XI selection and positional fit.", body,
                "Open First XI planner", "first-xi");
    }

    private Component aiPreview() {
        Div body = new Div();
        body.addClassName("overview-ai-body");
        Div status = new Div(new Span("FM AI"), new Span(snapshot.aiConfigured() ? "API ready" : "Setup required"));
        status.addClassName("overview-ai-status");
        Paragraph copy = new Paragraph(snapshot.aiConfigured()
                ? "Ask focused questions about the squad, tactic, or next transfer window."
                : "Connect OpenRouter in Settings to unlock contextual squad advice.");
        body.add(status, copy);
        String[] prompts = {"Where are we weakest?", "Who should we sell?", "Find XI upgrades"};
        for (String prompt : prompts) {
            Button chip = new Button(prompt, event -> ChatLaunch.open(prompt + " for " + snapshot.clubName() + "."));
            chip.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            chip.addClassName("overview-prompt-chip");
            body.add(chip);
        }
        return panel("FM AI chat", "Contextual prompts, with the full workspace one click away.", body,
                "Open AI workspace", "chat");
    }

    private Component shortlist() {
        Div body = new Div();
        body.addClassName("overview-shortlist-list");
        if (snapshot.shortlist().isEmpty()) {
            body.add(emptyPanel("No shortlist candidates available for this snapshot."));
        } else {
            int rank = 1;
            for (FmAiAssistentTools.TransferShortlistRow row : snapshot.shortlist()) {
                Div item = new Div();
                item.addClassName("overview-shortlist-row");
                int currentRank = rank++;
                item.getElement().setAttribute("role", "button");
                item.getElement().setAttribute("tabindex", "0");
                item.getElement().setAttribute("aria-label", "Open " + row.name() + " player dossier");
                item.getElement().executeJs("this.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); this.click(); } });");
                item.addClickListener(event -> PlayerDossier.openNamed(tools, row.name(), settings.currency(), snapshot.clubName()));
                Span number = new Span(String.valueOf(currentRank));
                number.addClassName("overview-shortlist-rank");
                Div text = new Div();
                Span name = new Span(row.name());
                name.addClassName("overview-shortlist-name");
                Span meta = new Span((row.club() == null ? "Unknown club" : row.club()) + " · CA " + row.ca() + " · fit " + percentage(row.roleFit()));
                meta.addClassName("overview-shortlist-meta");
                text.add(name, meta);
                Span fee = new Span(row.freeAgent() ? "Free" : money(row.askingPrice()));
                fee.addClassName("overview-shortlist-fee");
                item.add(number, text, fee);
                body.add(item);
            }
        }
        return panel("Top shortlist targets", "Best-ranked candidates from the current transfer pipeline.", body,
                "View full shortlist", "shortlist");
    }

    private Component depth() {
        Div body = new Div();
        body.addClassName("overview-depth-list");
        for (DashboardSnapshot.Depth row : snapshot.depth()) {
            Div line = new Div();
            line.addClassName("overview-depth-row");
            Span label = new Span(row.position());
            label.addClassName("overview-depth-label");
            Div track = new Div();
            track.addClassName("overview-depth-track");
            Div fill = new Div();
            fill.addClassName("overview-depth-fill");
            fill.getStyle().set("width", Math.min(100, Math.max(0, row.score() * 5)) + "%");
            track.add(fill);
            Span value = new Span(row.count() + " / " + (row.score() == 0 ? "—" : row.score()));
            value.addClassName("overview-depth-value");
            line.add(label, track, value);
            body.add(line);
        }
        return panel("Squad depth by position", "Natural-position strength across the selected squad.", body,
                "Open Player Desk", "desk");
    }

    private Component trim() {
        DashboardSnapshot.TrimSummary summary = snapshot.trim();
        Div body = new Div();
        body.addClassName("overview-trim-list");
        body.add(trimCard("Sell", summary.sell(), summary.knownValue() == null ? "Value unknown" : money(summary.knownValue()), "danger"),
                trimCard("Loan", summary.loan(), "Improve development", "warning"),
                trimCard("Keep", summary.keep(), "Core squad", "success"));
        return panel("Squad trim recommendations", "Clearer decisions for the next window.", body,
                "Review full squad trim", "squad-trim");
    }

    private Component trimCard(String label, int count, String detail, String tone) {
        Div card = new Div();
        card.addClassName("overview-trim-card");
        card.addClassName("overview-tone-" + tone);
        Span title = new Span(label);
        title.addClassName("overview-trim-label");
        Span number = new Span(String.valueOf(count));
        number.addClassName("overview-trim-count");
        Span copy = new Span(detail);
        copy.addClassName("overview-trim-detail");
        card.add(title, number, copy);
        return card;
    }

    private Component panel(String title, String hint, Component body, String action, String route) {
        Div panel = new Div();
        panel.addClassName("overview-panel");
        Div heading = new Div();
        heading.addClassName("overview-panel-heading");
        Div titles = new Div();
        Span titleText = new Span(title);
        titleText.addClassName("overview-panel-title");
        Span hintText = new Span(hint);
        hintText.addClassName("overview-panel-hint");
        titles.add(titleText, hintText);
        heading.add(titles);
        panel.add(heading, body);
        Button footer = new Button(action + "  →", event -> getUI().ifPresent(ui -> ui.navigate(route)));
        footer.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        footer.addClassName("overview-panel-link");
        panel.add(footer);
        return panel;
    }

    private Component metric(String label, String value, String detail, String tone, VaadinIcon icon) {
        Div card = new Div();
        card.addClassName("overview-metric");
        card.addClassName("overview-tone-" + tone);
        Div top = new Div(icon.create(), new Span(label));
        top.addClassName("overview-metric-label");
        Span main = new Span(value);
        main.addClassName("overview-metric-value");
        if ("Unknown".equals(value)) {
            main.addClassName("overview-metric-value-unknown");
        }
        Span copy = new Span(detail);
        copy.addClassName("overview-metric-detail");
        card.add(top, main, copy);
        return card;
    }

    private Component emptyClubState() {
        Div state = new Div();
        state.addClassName("overview-empty");
        state.add(new Span("FM AI ASSISTENT / OPERATIONS"), new H2("Choose a club to open the command center"),
                new Paragraph("Use the club selector in the top bar to focus the dashboard on a squad."));
        return state;
    }

    private Component emptySnapshot() {
        Div state = new Div();
        state.addClassName("overview-empty");
        state.add(new Span("FM AI ASSISTENT / DATA SOURCE"), new H2("Load a live squad snapshot"),
                new Paragraph("Load Football Manager data to unlock scouting, squad decisions, tactical analysis, and AI advice."));
        Button load = new Button("Load from RAM", VaadinIcon.DATABASE.create());
        load.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        load.addClickListener(event -> RamLoadUi.start(ramLoad, load));
        state.add(load);
        return state;
    }

    private void renderLoading() {
        removeAll();
        Div state = new Div();
        state.addClassName("overview-empty");
        state.addClassName("overview-loading");
        state.add(new Span("FM AI ASSISTENT / OPERATIONS"), new H2("Assembling the match plan…"),
                new Paragraph("Reading the current snapshot and building the decision board."));
        add(state);
    }

    private void renderError(Throwable error) {
        removeAll();
        Div state = new Div();
        state.addClassName("overview-empty");
        state.add(new Span("FM AI ASSISTENT / ERROR"), new H2("The command center could not load"),
                new Paragraph(error.getMessage() == null ? "Try refreshing the board." : error.getMessage()));
        Button retry = new Button("Retry", VaadinIcon.REFRESH.create(), event -> refresh());
        retry.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        state.add(retry);
        add(state);
        Notification.show("Dashboard refresh failed", 4000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private Component footerNote() {
        Span note = new Span(snapshot.heartbeat().label() + (snapshot.partial() ? " · Some panels are awaiting more complete data." : ""));
        note.addClassName("overview-footer-note");
        return note;
    }

    private static VaadinIcon iconFor(String route) {
        return switch (route) {
            case "contracts" -> VaadinIcon.WALLET;
            case "squad-trim" -> VaadinIcon.MINUS;
            case "shortlist" -> VaadinIcon.SEARCH;
            case "academy" -> VaadinIcon.ACADEMY_CAP;
            default -> VaadinIcon.ARROW_RIGHT;
        };
    }

    private static Component emptyPanel(String message) {
        Span empty = new Span(message);
        empty.addClassName("overview-panel-empty");
        return empty;
    }

    private String money(Long amount) {
        return amount == null || amount <= 0 ? "Unknown" : MoneyDisplay.format(amount, settings.currency());
    }

    private boolean tacticConfigured() {
        return !snapshot.tactical().formation().isBlank();
    }

    private static String percentage(Double fit) {
        return fit == null ? "unknown" : Math.round(fit) + "/20";
    }
}
