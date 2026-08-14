package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import com.github.fmaiassistent.mcp.FmAiAssistentTools;
import com.github.fmaiassistent.mcp.FmAiAssistentTools.TransferShortlistRow;
import com.github.fmaiassistent.mcp.PositionCodes;
import com.github.fmaiassistent.service.AppSettingsService;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.vaadin.flow.component.Component;
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

@Route(value = "shortlist", layout = AppShell.class)
@PageTitle("Shortlist")
@CssImport("./styles/moneyball-view.css")
@CssImport(value = "./styles/player-grid.css", themeFor = "vaadin-grid")
public class ShortlistView extends VerticalLayout {
    private final FmAiAssistentTools tools;
    private final ComboBox<String> clubFilter = new ComboBox<>("Club");
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

    public ShortlistView(FmAiAssistentTools tools, ClubDatabaseService clubs, AppSettingsService settings) {
        this.tools = tools;
        this.currency = settings.currency();
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
        clubFilter.setPlaceholder("Pick your club");
        clubFilter.setWidth("16em");
        minCa.setPlaceholder("any");
        minPa.setPlaceholder("any");
        maxAge.setPlaceholder("any");
        maxPrice.setPlaceholder("budget");
        maxWage.setPlaceholder("auto");
        runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        runButton.addClickListener(event -> run());
        clubFilter.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                run();
            }
        });
        HorizontalLayout bar = new HorizontalLayout(
                clubFilter, positionFilter, roleFilter, minCa, minPa, maxAge, maxPrice, maxWage, wonderkids, runButton);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.END);
        bar.addClassName("moneyball-filters");
        return bar;
    }

    private void configureGrid() {
        grid.addClassName("moneyball-grid");
        grid.setEmptyStateText("Pick a club to rank signings.");
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
        grid.addColumn(row -> String.join(", ", row.signals())).setHeader("Signals").setFlexGrow(1);
    }

    private void run() {
        String club = clubFilter.getValue();
        if (club == null || club.isBlank()) {
            Notification.show("Pick a club first", 3000, Notification.Position.MIDDLE)
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
        runButton.setEnabled(false);
        try {
            List<TransferShortlistRow> rows = tools.transferShortlistRows(
                    club, position, role, ageCap, value(minCa), value(minPa),
                    value(maxPrice) == null ? null : value(maxPrice).longValue(), value(maxWage));
            grid.setItems(rows);
            summary.setText(rows.size() + " candidates · same ranking as fm26_transfer_shortlist"
                    + (Boolean.TRUE.equals(wonderkids.getValue()) ? " with wonderkid age cap" : ""));
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        } finally {
            runButton.setEnabled(true);
        }
    }

    private static Integer value(IntegerField field) {
        return field.isEmpty() ? null : field.getValue();
    }

    private static String capitalize(String value) {
        return value == null || value.isEmpty() ? value
                : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
