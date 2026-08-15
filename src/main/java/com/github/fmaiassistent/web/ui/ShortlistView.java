package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.ClubEntity;
import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import com.github.fmaiassistent.mcp.FmAiAssistentTools;
import com.github.fmaiassistent.mcp.FmAiAssistentTools.TransferShortlistRow;
import com.github.fmaiassistent.mcp.PositionCodes;
import com.github.fmaiassistent.service.AppSettingsService;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
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
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Route(value = "shortlist", layout = AppShell.class)
@PageTitle("Shortlist")
@CssImport("./styles/moneyball-view.css")
@CssImport(value = "./styles/player-grid.css", themeFor = "vaadin-grid")
public class ShortlistView extends VerticalLayout {
    private final FmAiAssistentTools tools;
    private final String sessionClub;
    private final ComboBox<String> positionFilter = new ComboBox<>("Position");
    private final ComboBox<String> roleFilter = new ComboBox<>("In-possession role");
    private final IntegerField minCa = new IntegerField("Min CA");
    private final IntegerField minPa = new IntegerField("Min PA");
    private final IntegerField maxAge = new IntegerField("Max age");
    private final IntegerField maxPrice = new IntegerField();
    private final IntegerField maxWage = new IntegerField();
    private final Checkbox wonderkids = new Checkbox("Wonderkids (max age 21)");
    private final Button runButton = new Button("Shortlist", VaadinIcon.SEARCH.create());
    private final Span summary = new Span();
    private final Grid<TransferShortlistRow> grid = new Grid<>();
    private final MoneyCurrency currency;
    private Map<String, ClubEntity> clubsByName = Map.of();

    public ShortlistView(FmAiAssistentTools tools, ClubDatabaseService clubs, AppSettingsService settings) {
        this.tools = tools;
        this.currency = settings.currency();
        this.sessionClub = SessionClub.resolved(settings, SessionClub.names(clubs));
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("moneyball-view");
        maxPrice.setLabel("Max fee (" + currency.symbol() + ")");
        maxWage.setLabel("Max wage/wk (" + currency.symbol() + ")");
        configureGrid();
        add(header(), filterBar(), summary, grid);
        expand(grid);
        summary.addClassName("moneyball-summary");
        if (!clubs.hasClubs()) {
            grid.setVisible(false);
            summary.setText("Load from the top bar with FM26 running.");
            summary.addClassName("moneyball-empty");
            return;
        }
        clubsByName = clubs.findAllClubs().stream()
                .filter(club -> club.getName() != null && !club.getName().isBlank())
                .collect(Collectors.toMap(ClubEntity::getName, club -> club, (left, right) -> left));
        if (sessionClub.isBlank()) {
            summary.setText("Pick your club in the top bar.");
            summary.addClassName("moneyball-empty");
            return;
        }
        run();
    }

    private Component header() {
        Span hint = new Span(
                "Tactical buys ranked like fm26_transfer_shortlist. Tick wonderkids to use the fm26_wonderkid_shortlist age cap.");
        hint.addClassName("moneyball-hint");
        hint.setWidthFull();
        return hint;
    }

    private HorizontalLayout filterBar() {
        List<String> positions = new ArrayList<>();
        positions.add("Any");
        positions.addAll(PositionCodes.CODES);
        positionFilter.setItems(positions);
        positionFilter.setValue("Any");
        positionFilter.setWidth("7.5em");
        List<String> roles = new ArrayList<>();
        roles.add("Any");
        roles.addAll(PlayerRoleAttributeCatalog.roles(PlayerRoleAttributeCatalog.IN_POSSESSION));
        roleFilter.setItems(roles);
        roleFilter.setValue("Any");
        roleFilter.setWidth("16em");
        minCa.setPlaceholder("any");
        minPa.setPlaceholder("any");
        maxAge.setPlaceholder("any");
        maxPrice.setPlaceholder("budget");
        maxWage.setPlaceholder("auto");
        runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        runButton.addClickListener(event -> run());
        HorizontalLayout bar = new HorizontalLayout(
                positionFilter, roleFilter, minCa, minPa, maxAge, maxPrice, maxWage, wonderkids, runButton);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.END);
        bar.addClassName("moneyball-filters");
        return bar;
    }

    private void configureGrid() {
        grid.addClassName("moneyball-grid");
        grid.setEmptyStateText("Pick your club in the top bar to rank signings.");
        grid.addItemClickListener(event -> {
            if (event.getColumn() != null && "ask".equals(event.getColumn().getKey())) {
                return;
            }
            PlayerDossier.openNamed(tools, event.getItem().name(), currency, sessionClub);
        });
        grid.addComponentColumn(row -> ChatLaunch.askButton(row.name(), sessionClub))
                .setHeader("")
                .setKey("ask")
                .setWidth("3.5em")
                .setFlexGrow(0);
        grid.addColumn(TransferShortlistRow::rank).setHeader("Rank").setWidth("4.5em").setFlexGrow(0);
        grid.addColumn(row -> String.format(Locale.ROOT, "%.1f", row.score())).setHeader("Score").setWidth("5em").setFlexGrow(0);
        grid.addColumn(TransferShortlistRow::name).setHeader("Name").setAutoWidth(true);
        grid.addColumn(TransferShortlistRow::age).setHeader("Age").setWidth("4em").setFlexGrow(0);
        grid.addColumn(TransferShortlistRow::positionScore).setHeader("Pos").setWidth("4em").setFlexGrow(0);
        grid.addColumn(row -> row.roleFit() == null ? "" : String.format(Locale.ROOT, "%.1f", row.roleFit()))
                .setHeader("Role fit").setWidth("6em").setFlexGrow(0);
        grid.addColumn(TransferShortlistRow::ca).setHeader("CA").setWidth("4em").setFlexGrow(0);
        grid.addColumn(TransferShortlistRow::pa).setHeader("PA").setWidth("4em").setFlexGrow(0);
        grid.addColumn(TransferShortlistRow::developmentUpside).setHeader("Upside").setWidth("5em").setFlexGrow(0);
        grid.addColumn(TransferShortlistRow::club).setHeader("Club");
        grid.addColumn(TransferShortlistRow::nationality).setHeader("Nation");
        grid.addColumn(row -> row.freeAgent() ? "Free" : row.askingPrice() == null ? "Unknown"
                : MoneyDisplay.format(row.askingPrice(), currency)).setHeader("Fee");
        grid.addColumn(row -> MoneyDisplay.format(row.salaryWeekly(), currency)).setHeader("Wage/wk");
        grid.addColumn(row -> capitalize(row.willingness())).setHeader("Willingness");
        grid.addColumn(row -> row.transferListed() ? "Listed" : "").setHeader("Listed")
                .setWidth("5.5em").setFlexGrow(0);
        grid.addColumn(row -> row.injured() ? "Inj" : "").setHeader("Inj")
                .setWidth("4em").setFlexGrow(0);
        grid.addColumn(row -> String.join(", ", row.signals())).setHeader("Signals").setFlexGrow(1);
    }

    private void run() {
        String club = sessionClub;
        if (club.isBlank()) {
            Notification.show("Pick your club in the top bar", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        String position = "Any".equals(positionFilter.getValue()) ? null : positionFilter.getValue();
        String role = "Any".equals(roleFilter.getValue()) ? null : roleFilter.getValue();
        if (role != null && position == null) {
            Notification.show("Pick a position before choosing a role", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        Integer ageCap = value(maxAge);
        if (Boolean.TRUE.equals(wonderkids.getValue())) {
            ageCap = ageCap == null ? 21 : Math.min(ageCap, 21);
        }
        Integer finalAgeCap = ageCap;
        UI ui = UI.getCurrent();
        runButton.setEnabled(false);
        CompletableFuture.supplyAsync(() ->
                tools.transferShortlistRows(
                        club, position, role, finalAgeCap, value(minCa), value(minPa),
                        feePounds(), wagePounds())
        ).thenAccept(rows -> ui.access(() -> {
            grid.setItems(rows);
            long listed = rows.stream().filter(TransferShortlistRow::transferListed).count();
            long injured = rows.stream().filter(TransferShortlistRow::injured).count();
            summary.setText(rows.size() + " candidates · " + listed + " listed · " + injured + " injured"
                    + budgetSummary(club)
                    + " · same ranking as fm26_transfer_shortlist"
                    + (Boolean.TRUE.equals(wonderkids.getValue()) ? " with wonderkid age cap" : ""));
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

    private String budgetSummary(String clubName) {
        ClubEntity club = clubsByName.get(clubName);
        if (club == null) {
            return "";
        }
        String fee = club.getTransferBudget() == null ? "unknown" : MoneyDisplay.format(club.getTransferBudget(), currency);
        String wage = club.getPayrollBudget() == null ? "unknown" : MoneyDisplay.format(club.getPayrollBudget(), currency);
        return " · transfer budget " + fee + " · wage budget/wk " + wage;
    }

    private Long feePounds() {
        Integer displayed = value(maxPrice);
        return displayed == null ? null : MoneyDisplay.toBasePounds(displayed.longValue(), currency);
    }

    private Integer wagePounds() {
        Integer displayed = value(maxWage);
        if (displayed == null) {
            return null;
        }
        long pounds = MoneyDisplay.toBasePounds(displayed.longValue(), currency);
        return pounds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pounds;
    }

    private static Integer value(IntegerField field) {
        return field.isEmpty() ? null : field.getValue();
    }

    private static String capitalize(String value) {
        return value == null || value.isEmpty() ? value
                : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
