package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.ClubEntity;
import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.service.AppSettingsService;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.github.fmaiassistent.service.PlayerDatabaseService;
import com.github.fmaiassistent.service.RamLoadCoordinator;
import com.vaadin.flow.component.Component;
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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Route(value = "", layout = AppShell.class)
@PageTitle("Overview")
@CssImport("./styles/overview-view.css")
public class OverviewView extends VerticalLayout {
    private final PlayerDatabaseService players;
    private final ClubDatabaseService clubs;
    private final AppSettingsService settings;
    private final RamLoadCoordinator ramLoad;

    public OverviewView(
            PlayerDatabaseService players,
            ClubDatabaseService clubs,
            AppSettingsService settings,
            RamLoadCoordinator ramLoad) {
        this.players = players;
        this.clubs = clubs;
        this.settings = settings;
        this.ramLoad = ramLoad;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("overview-view");
        render();
    }

    private void render() {
        removeAll();
        long playerCount = players.countPlayers();
        SnapshotHeartbeat.Status snapshot = SnapshotHeartbeat.from(players.metadata(), playerCount);
        List<String> clubNames = SessionClub.names(clubs);
        String sessionClub = SessionClub.resolved(settings, clubNames);
        if (snapshot.empty()) {
            add(emptySnapshot());
            return;
        }

        List<PlayerEntity> all = players.findAllPlayerEntities();
        List<PlayerEntity> squad = sessionClub.isBlank()
                ? List.of()
                : all.stream().filter(player -> belongsTo(player, sessionClub)).toList();
        ClubEntity club = findClub(sessionClub);

        add(pageHeader(sessionClub, snapshot));
        add(insights(all, squad, club));
        add(priorityActions(sessionClub, squad, club));
        add(squadPulse(squad));
    }

    private Component emptySnapshot() {
        Div state = new Div();
        state.addClassName("overview-empty");
        Span eyebrow = new Span("FM AI ASSISTENT");
        eyebrow.addClassName("overview-eyebrow");
        H2 title = new H2("Start with a live squad snapshot");
        Paragraph copy = new Paragraph("Load Football Manager data to unlock scouting, squad decisions, tactical analysis, and AI advice.");
        Button load = new Button("Load from RAM", VaadinIcon.DATABASE.create(), event -> RamLoadUi.start(ramLoad, event.getSource()));
        load.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button desk = new Button("Explore the player desk", event -> getUI().ifPresent(ui -> ui.navigate("desk")));
        desk.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout actions = new HorizontalLayout(load, desk);
        actions.addClassName("overview-empty-actions");
        state.add(eyebrow, title, copy, actions);
        return state;
    }

    private Component pageHeader(String club, SnapshotHeartbeat.Status snapshot) {
        Div copy = new Div();
        Span eyebrow = new Span("CLUB OPERATIONS");
        eyebrow.addClassName("overview-eyebrow");
        H2 title = new H2(club.isBlank() ? "Your club, at a glance" : club + " command center");
        Paragraph subtitle = new Paragraph(snapshot.stale()
                ? "Your snapshot needs a refresh before making transfer or squad decisions."
                : "Review the decisions that matter before you open a specialist workspace.");
        copy.add(eyebrow, title, subtitle);

        Button load = new Button(snapshot.stale() ? "Refresh snapshot" : "Load latest", VaadinIcon.REFRESH.create(),
                event -> RamLoadUi.start(ramLoad, event.getSource()));
        load.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button ask = new Button("Ask FM AI", VaadinIcon.CHAT.create(), event ->
                ChatLaunch.open("What should I focus on for my club right now?"));
        ask.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout actions = new HorizontalLayout(load, ask);
        actions.addClassName("overview-header-actions");
        Div header = new Div(copy, actions);
        header.addClassName("overview-header");
        return header;
    }

    private Component insights(List<PlayerEntity> all, List<PlayerEntity> squad, ClubEntity club) {
        long injured = squad.stream().filter(player -> Boolean.TRUE.equals(player.getInjured())).count();
        long expiring = squad.stream().filter(player -> player.getContractEndDate() != null && !player.getContractEndDate().isBlank()).count();
        int averageCa = averageCa(squad.isEmpty() ? all : squad);
        long wage = squad.stream().map(PlayerEntity::getSalaryWeeklyRaw).filter(value -> value != null)
                .mapToLong(Integer::longValue).sum();

        Div cards = new Div(
                insight("Squad health", squad.isEmpty() ? "Choose your club" : injured + " unavailable", squad.isEmpty()
                        ? "Select a club in the top bar to focus this workspace."
                        : injured == 0 ? "No recorded injuries in this snapshot." : "Review availability before match day.", "health"),
                insight("Current level", averageCa == 0 ? "—" : "CA " + averageCa,
                        squad.isEmpty() ? all.size() + " players in the snapshot" : squad.size() + " players in your club", "ability"),
                insight("Contract watch", expiring == 0 ? "No dates" : expiring + " tracked", "Open Contracts to prioritise renewals and exits.", "contract"),
                insight("Weekly wages", wage == 0 ? "Unknown" : MoneyDisplay.format(wage, settings.currency()),
                        club == null || club.getPayrollBudget() == null ? "Payroll budget unavailable." : "Payroll budget "
                                + MoneyDisplay.format(club.getPayrollBudget(), settings.currency()), "wage"));
        cards.addClassName("overview-insights");
        return cards;
    }

    private Component priorityActions(String club, List<PlayerEntity> squad, ClubEntity clubEntity) {
        Div section = new Div();
        section.addClassName("overview-section");
        section.add(sectionTitle("Priority actions", "Jump straight into the next football decision."));
        Div actions = new Div(
                action("Find an upgrade", "Build a tactical shortlist for a weak position.", VaadinIcon.SEARCH, "shortlist"),
                action("Pick your best XI", "Use your live squad and tactical roles.", VaadinIcon.CLIPBOARD_TEXT, "first-xi"),
                action("Review contracts", "Resolve upcoming renewals, sales, and loans.", VaadinIcon.WALLET, "contracts"),
                action("Trim the squad", "Identify sell, loan, keep, and review calls.", VaadinIcon.MINUS, "squad-trim"));
        actions.addClassName("overview-actions");
        section.add(actions);
        return section;
    }

    private Component squadPulse(List<PlayerEntity> squad) {
        Div section = new Div();
        section.addClassName("overview-section");
        section.addClassName("overview-pulse");
        section.add(sectionTitle("Squad pulse", squad.isEmpty()
                ? "Select a club to see the players who need attention."
                : "Highest current ability in your selected squad."));
        Div rows = new Div();
        rows.addClassName("overview-player-list");
        squad.stream().sorted(Comparator.comparingInt(OverviewView::ca).reversed()).limit(5)
                .forEach(player -> rows.add(playerRow(player)));
        if (squad.isEmpty()) {
            rows.add(new Span("No club-specific players are available yet."));
        }
        section.add(rows);
        return section;
    }

    private Component action(String title, String copy, VaadinIcon icon, String route) {
        Button button = new Button();
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        button.addClassName("overview-action-card");
        button.setIcon(icon.create());
        Div text = new Div(new Span(title), new Span(copy));
        text.addClassName("overview-action-copy");
        button.setSuffixComponent(text);
        button.addClickListener(event -> getUI().ifPresent(ui -> ui.navigate(route)));
        return button;
    }

    private Component playerRow(PlayerEntity player) {
        String name = player.getName() == null ? "Unknown player" : player.getName();
        Span title = new Span(name);
        title.addClassName("overview-player-name");
        Span meta = new Span(PositionTextFormatter.format(player) + " · CA " + ca(player));
        meta.addClassName("overview-player-meta");
        Button row = new Button(title, event -> PlayerDossier.open(player, settings.currency(), SessionClub.resolved(settings, SessionClub.names(clubs))));
        row.setSuffixComponent(meta);
        row.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        row.addClassName("overview-player-row");
        return row;
    }

    private Component sectionTitle(String title, String copy) {
        Div wrap = new Div();
        Span heading = new Span(title);
        heading.addClassName("overview-section-title");
        Span hint = new Span(copy);
        hint.addClassName("overview-section-hint");
        wrap.add(heading, hint);
        return wrap;
    }

    private Component insight(String label, String value, String copy, String tone) {
        Div card = new Div();
        card.addClassName("overview-insight");
        card.getElement().setAttribute("data-tone", tone);
        Span labelText = new Span(label);
        labelText.addClassName("overview-insight-label");
        Span valueText = new Span(value);
        valueText.addClassName("overview-insight-value");
        Span copyText = new Span(copy);
        copyText.addClassName("overview-insight-copy");
        card.add(labelText, valueText, copyText);
        return card;
    }

    private ClubEntity findClub(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return clubs.requireNamed(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean belongsTo(PlayerEntity player, String club) {
        return equalsIgnoreCase(player.getClub(), club) || equalsIgnoreCase(player.getPlayingClub(), club);
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static int averageCa(List<PlayerEntity> players) {
        return players.stream().map(PlayerEntity::getCa).filter(value -> value != null && value > 0)
                .mapToInt(Integer::intValue).average().stream().mapToInt(value -> (int) Math.round(value)).findFirst().orElse(0);
    }

    private static int ca(PlayerEntity player) {
        return player.getCa() == null ? 0 : player.getCa();
    }
}
