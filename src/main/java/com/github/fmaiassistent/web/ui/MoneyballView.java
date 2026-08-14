package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import com.github.fmaiassistent.mcp.FmAiAssistentTools;
import com.github.fmaiassistent.mcp.FmAiAssistentTools.MoneyballResult;
import com.github.fmaiassistent.mcp.FmAiAssistentTools.MoneyballRow;
import com.github.fmaiassistent.service.AppSettingsService;
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
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.progressbar.ProgressBarVariant;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Moneyball scouting view: every value signing for a club ranked by signing_rating,
 * sharing the exact rating pipeline with the fm26_moneyball_shortlist MCP tool.
 */
@Route(value = "moneyball", layout = AppShell.class)
@PageTitle("Moneyball")
@CssImport("./styles/moneyball-view.css")
@CssImport(value = "./styles/player-grid.css", themeFor = "vaadin-grid")
public class MoneyballView extends VerticalLayout {

    private static final List<String> POSITIONS = List.of(
            "GK", "DL", "DC", "DR", "WBL", "DMC", "WBR", "ML", "MC", "MR", "AML", "AMC", "AMR", "ST");

    private final FmAiAssistentTools tools;

    private final ComboBox<String> clubFilter = new ComboBox<>("Club");
    private final ComboBox<String> positionFilter = new ComboBox<>("Position");
    private final IntegerField minCa = new IntegerField("Min CA");
    private final IntegerField minPa = new IntegerField("Min PA");
    private final IntegerField maxAge = new IntegerField("Max age");
    private final IntegerField maxPrice = new IntegerField();
    private final IntegerField maxWage = new IntegerField();
    private final Button runButton = new Button("Find value", VaadinIcon.DIPLOMA.create());
    private final Span summary = new Span();
    private final Grid<MoneyballRow> grid = new Grid<>();
    private final MoneyCurrency currency;

    public MoneyballView(FmAiAssistentTools tools, ClubDatabaseService clubs, AppSettingsService settings) {
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
            summary.setText("No FM data in the H2 database yet - open the Scouting desk and click \"Load from RAM\" first.");
            summary.addClassName("moneyball-empty");
            return;
        }
        clubFilter.setItems(clubNames(clubs));
    }

    private Component header() {
        Span hint = new Span(
                "signing_rating (0-100) = quality \u00d7 value: half CA, half age-adjusted PA, adjusted by fee + 3 years of wages against the market median");
        hint.addClassName("moneyball-hint");
        hint.setWidthFull();
        return hint;
    }

    private HorizontalLayout filterBar() {
        List<String> positions = new ArrayList<>();
        positions.add("Any");
        positions.addAll(POSITIONS);
        positionFilter.setItems(positions);
        positionFilter.setValue("Any");
        positionFilter.setWidth("7.5em");

        clubFilter.setPlaceholder("Pick your club");
        clubFilter.setWidth("16em");
        minCa.setPlaceholder("auto");
        minPa.setPlaceholder("any");
        maxAge.setPlaceholder("any");
        maxPrice.setPlaceholder("budget");
        maxWage.setPlaceholder("auto");

        runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        runButton.addClassName("moneyball-run");
        runButton.addClickListener(event -> run());
        clubFilter.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                run();
            }
        });

        HorizontalLayout bar = new HorizontalLayout(
                clubFilter, positionFilter, minCa, minPa, maxAge, maxPrice, maxWage, runButton);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.END);
        bar.addClassName("moneyball-filters");
        return bar;
    }

    private void configureGrid() {
        grid.addClassName("moneyball-grid");
        grid.setSelectionMode(Grid.SelectionMode.NONE);
        grid.setEmptyStateText("Pick a club to see value signings.");

        grid.addColumn(MoneyballRow::rank).setHeader("Rank").setSortable(true).setWidth("4.5em").setFlexGrow(0);
        grid.addColumn(ratingRenderer()).setHeader("Signing")
                .setSortable(true)
                .setComparator(Comparator.comparingInt(MoneyballRow::signingRating))
                .setWidth("8.5em").setFlexGrow(0);
        grid.addColumn(tierRenderer()).setHeader("Deal")
                .setSortable(true)
                .setComparator(Comparator.comparingDouble(row -> row.deal().score()))
                .setWidth("7em").setFlexGrow(0);
        grid.addColumn(MoneyballRow::name).setHeader("Name").setSortable(true).setAutoWidth(true);
        grid.addColumn(MoneyballRow::age).setHeader("Age").setSortable(true).setWidth("4em").setFlexGrow(0);
        grid.addColumn(MoneyballRow::positionScore).setHeader("Pos").setSortable(true).setWidth("4em").setFlexGrow(0);
        grid.addColumn(MoneyballRow::ca).setHeader("CA").setSortable(true).setWidth("4em").setFlexGrow(0);
        grid.addColumn(MoneyballRow::pa).setHeader("PA").setSortable(true).setWidth("4em").setFlexGrow(0);
        grid.addColumn(MoneyballRow::developmentUpside).setHeader("Upside").setSortable(true).setWidth("5em").setFlexGrow(0);
        grid.addColumn(MoneyballRow::club).setHeader("Club").setSortable(true);
        grid.addColumn(MoneyballRow::nationality).setHeader("Nation").setSortable(true);
        grid.addColumn(row -> String.format(Locale.ROOT, "%.2f", row.deal().score())).setHeader("Deal score")
                .setSortable(true)
                .setComparator(Comparator.comparingDouble(row -> row.deal().score()))
                .setWidth("6em").setFlexGrow(0);
        grid.addColumn(row -> money(row.deal().market().price())).setHeader("Market value")
                .setSortable(true)
                .setComparator(Comparator.comparingLong(row -> row.deal().market().price()));
        grid.addColumn(feeRenderer()).setHeader("Fee")
                .setSortable(true)
                .setComparator(Comparator.comparingLong(row -> row.costFee()));
        grid.addColumn(row -> money(row.deal().totalCost())).setHeader("3-yr cost")
                .setSortable(true)
                .setComparator(Comparator.comparingLong(row -> row.deal().totalCost()));
        grid.addColumn(gapRenderer()).setHeader("Saving vs market")
                .setSortable(true)
                .setComparator(Comparator.comparingLong(row -> row.deal().marketCost() - row.deal().totalCost()));
        grid.addColumn(row -> money(row.salaryWeekly())).setHeader("Wage/wk")
                .setSortable(true)
                .setComparator(Comparator.comparingLong(MoneyballRow::salaryWeekly));
        grid.addColumn(row -> row.deal().market().samples()).setHeader("Samples")
                .setSortable(true)
                .setComparator(Comparator.comparingInt(row -> row.deal().market().samples()));
        grid.addColumn(row -> capitalize(row.willingness())).setHeader("Willingness").setSortable(true);
    }

    private void run() {
        String clubName = clubFilter.getValue();
        if (clubName == null || clubName.isBlank()) {
            Notification.show("Pick a club first", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        String position = "Any".equals(positionFilter.getValue()) ? null : positionFilter.getValue();
        try {
            MoneyballResult result = tools.moneyballRows(
                    clubName,
                    position,
                    value(minCa),
                    value(minPa),
                    value(maxAge),
                    value(maxPrice) == null ? null : value(maxPrice).longValue(),
                    value(maxWage));
            grid.setEmptyStateText("No candidates match these filters.");
            grid.setItems(result.rows());
            summary.setText("Pool " + result.candidatePoolSize() + " \u00b7 rated " + result.ratedCount()
                    + " \u00b7 market model: " + result.pricedPlayers() + " priced players in "
                    + result.bucketCount() + " buckets \u00b7 sorted by signing_rating");
            summary.removeClassName("moneyball-empty");
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private static ComponentRenderer<HorizontalLayout, MoneyballRow> ratingRenderer() {
        return new ComponentRenderer<>(row -> {
            ProgressBar bar = new ProgressBar(0, 100, row.signingRating());
            bar.addThemeVariants(ProgressBarVariant.LUMO_SUCCESS);
            bar.setWidth("4.5em");
            bar.setHeight("0.4em");
            Span value = new Span(String.valueOf(row.signingRating()));
            value.addClassName("rating-value");
            HorizontalLayout cell = new HorizontalLayout(bar, value);
            cell.setSpacing(true);
            cell.setAlignItems(FlexComponent.Alignment.CENTER);
            cell.addClassName("rating-cell");
            return cell;
        });
    }

    private static ComponentRenderer<Span, MoneyballRow> tierRenderer() {
        return new ComponentRenderer<>(row -> {
            Span badge = new Span(row.deal().tier());
            badge.addClassName("tier-badge");
            badge.addClassName("tier-" + row.deal().tier());
            return badge;
        });
    }

    private ComponentRenderer<Span, MoneyballRow> feeRenderer() {
        return new ComponentRenderer<>(row ->
                new Span(row.freeAgent() ? "Free" : money(row.costFee())));
    }

    private ComponentRenderer<Span, MoneyballRow> gapRenderer() {
        return new ComponentRenderer<>(row -> {
            long gap = row.deal().marketCost() - row.deal().totalCost();
            Span value = new Span((gap >= 0 ? "+" : "\u2212") + money(Math.abs(gap)));
            value.addClassName(gap >= 0 ? "gap-positive" : "gap-negative");
            return value;
        });
    }

    private static List<String> clubNames(ClubDatabaseService clubs) {
        return clubs.findAllClubs().stream()
                .map(club -> club.getName())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static Integer value(IntegerField field) {
        return field.isEmpty() ? null : field.getValue();
    }

    private String money(long pounds) {
        return MoneyDisplay.format(pounds, currency);
    }

    private static String capitalize(String value) {
        return value == null || value.isEmpty() ? value
                : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}