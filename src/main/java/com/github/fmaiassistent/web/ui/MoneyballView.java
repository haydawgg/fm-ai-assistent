package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import com.github.fmaiassistent.mcp.FmAiAssistentTools;
import com.github.fmaiassistent.mcp.FmAiAssistentTools.MoneyballResult;
import com.github.fmaiassistent.mcp.FmAiAssistentTools.MoneyballRow;
import com.github.fmaiassistent.service.AppSettingsService;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
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
import java.util.concurrent.CompletableFuture;

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

    private final String sessionClub;
    private final ComboBox<String> positionFilter = new ComboBox<>("Position");
    private final IntegerField minCa = new IntegerField("Min CA");
    private final IntegerField minPa = new IntegerField("Min PA");
    private final IntegerField maxAge = new IntegerField("Max age");
    private final IntegerField maxPrice = new IntegerField();
    private final IntegerField maxWage = new IntegerField();
    private final Button runButton = new Button("Find value", VaadinIcon.DIPLOMA.create());
    private final Span summary = new Span();
    private final Div dealCards = new Div();
    private final Grid<MoneyballRow> grid = new Grid<>();
    private final MoneyCurrency currency;

    public MoneyballView(FmAiAssistentTools tools, ClubDatabaseService clubs, AppSettingsService settings) {
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
        dealCards.addClassName("deal-card-strip");
        add(header(), filterBar(), summary, dealCards, grid);
        expand(grid);
        summary.addClassName("moneyball-summary");

        if (clubs.findAllClubs().isEmpty()) {
            grid.setVisible(false);
            summary.setText("Load from the top bar with FM26 running.");
            summary.addClassName("moneyball-empty");
            return;
        }
        if (sessionClub.isBlank()) {
            summary.setText("Pick your club in the top bar.");
            summary.addClassName("moneyball-empty");
            return;
        }
        run();
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

        minCa.setPlaceholder("auto");
        minPa.setPlaceholder("any");
        maxAge.setPlaceholder("any");
        maxPrice.setPlaceholder("budget");
        maxWage.setPlaceholder("auto");

        runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        runButton.addClassName("moneyball-run");
        runButton.addClickListener(event -> run());

        HorizontalLayout bar = new HorizontalLayout(
                positionFilter, minCa, minPa, maxAge, maxPrice, maxWage, runButton);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.END);
        bar.addClassName("moneyball-filters");
        return bar;
    }

    private void configureGrid() {
        grid.addClassName("moneyball-grid");
        grid.setSelectionMode(Grid.SelectionMode.NONE);
        grid.setEmptyStateText("Pick your club in the top bar to see value signings.");
        grid.addItemClickListener(event -> PlayerDossier.openNamed(tools, event.getItem().name(), currency, sessionClub));

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
        String clubName = sessionClub;
        if (clubName.isBlank()) {
            Notification.show("Pick your club in the top bar", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        String position = "Any".equals(positionFilter.getValue()) ? null : positionFilter.getValue();
        UI ui = UI.getCurrent();
        runButton.setEnabled(false);
        CompletableFuture.supplyAsync(() ->
                tools.moneyballRows(
                        clubName,
                        position,
                        value(minCa),
                        value(minPa),
                        value(maxAge),
                        feePounds(),
                        wagePounds())
        ).thenAccept(result -> ui.access(() -> {
            grid.setEmptyStateText("No candidates match these filters.");
            grid.setItems(result.rows());
            renderDealCards(result.rows());
            summary.setText("Pool " + result.candidatePoolSize() + " · rated " + result.ratedCount()
                    + " · market model: " + result.pricedPlayers() + " priced players in "
                    + result.bucketCount() + " buckets · sorted by signing_rating");
            summary.removeClassName("moneyball-empty");
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

    private void renderDealCards(List<MoneyballRow> rows) {
        dealCards.removeAll();
        int shown = 0;
        for (MoneyballRow row : rows) {
            if (shown >= 12) {
                break;
            }
            dealCards.add(dealCard(row));
            shown++;
        }
        dealCards.setVisible(shown > 0);
    }

    private Div dealCard(MoneyballRow row) {
        Span tier = new Span(row.deal().tier());
        tier.addClassName("tier-badge");
        tier.addClassName("tier-" + row.deal().tier());
        Span name = new Span(row.name());
        name.addClassName("deal-card-name");
        Span meta = new Span((row.age() == null ? "" : row.age() + " · ")
                + (row.club() == null ? "" : row.club()));
        meta.addClassName("deal-card-meta");
        Span rating = new Span("Signing " + row.signingRating());
        rating.addClassName("rating-value");
        long gap = row.deal().marketCost() - row.deal().totalCost();
        Span why = new Span(gap >= 0
                ? "Cheap by " + money(gap) + " vs market (fee + 3-yr wages)"
                : "Over market by " + money(Math.abs(gap)) + " on fee + 3-yr wages");
        why.addClassName("deal-card-why");
        Span cost = new Span((row.freeAgent() ? "Free" : money(row.costFee())) + " · 3-yr " + money(row.deal().totalCost()));
        cost.addClassName("deal-card-cost");
        Button dossier = new Button("Dossier", event -> PlayerDossier.openNamed(tools, row.name(), currency, sessionClub));
        dossier.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        Button explain = new Button("Explain in Chat", event ->
                ChatLaunch.open(ChatLaunch.explainDeal(row.name(), sessionClub)));
        explain.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        HorizontalLayout actions = new HorizontalLayout(dossier, explain);
        actions.setSpacing(true);
        Div card = new Div(tier, name, meta, rating, cost, why, actions);
        card.addClassName("deal-card");
        card.addClassName("deal-card-" + row.deal().tier());
        return card;
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

    private String money(long pounds) {
        return MoneyDisplay.format(pounds, currency);
    }

    private static String capitalize(String value) {
        return value == null || value.isEmpty() ? value
                : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}