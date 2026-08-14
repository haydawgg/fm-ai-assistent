package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import com.github.fmaiassistent.mcp.FmAiAssistentTools;
import com.github.fmaiassistent.mcp.SquadAdvice;
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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.List;

@Route("squad-trim")
@PageTitle("Squad trim")
@CssImport("./styles/moneyball-view.css")
public class SquadTrimView extends VerticalLayout {
    private final FmAiAssistentTools tools;
    private final ComboBox<String> clubFilter = new ComboBox<>("Club");
    private final Button runButton = new Button("Rank squad", VaadinIcon.MINUS.create());
    private final Span summary = new Span();
    private final Grid<SquadAdvice.SellRow> grid = new Grid<>();
    private final MoneyCurrency currency;

    public SquadTrimView(FmAiAssistentTools tools, ClubDatabaseService clubs, AppSettingsService settings) {
        this.tools = tools;
        this.currency = settings.currency();
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        addClassName("moneyball-view");
        configureGrid();
        add(header(), filterBar(), summary, grid);
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
        Span title = new Span("Squad trim");
        title.addClassName("moneyball-title");
        Span hint = new Span("Sell, loan or keep: depth, CA vs first team, wages and contracts. Same ranking as fm26_sell_shortlist.");
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
        clubFilter.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                run();
            }
        });
        HorizontalLayout bar = new HorizontalLayout(clubFilter, runButton);
        bar.setAlignItems(FlexComponent.Alignment.END);
        bar.addClassName("moneyball-filters");
        return bar;
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.setEmptyStateText("Pick a club to rank the squad.");
        grid.addColumn(SquadAdvice.SellRow::rank).setHeader("Rank").setWidth("4.5em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.SellRow::recommendation).setHeader("Call").setWidth("6em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.SellRow::name).setHeader("Name").setAutoWidth(true);
        grid.addColumn(SquadAdvice.SellRow::position).setHeader("Pos").setWidth("4em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.SellRow::age).setHeader("Age").setWidth("4em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.SellRow::ca).setHeader("CA").setWidth("4em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.SellRow::pa).setHeader("PA").setWidth("4em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.SellRow::caVsFirstTeam).setHeader("vs XI").setWidth("5em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.SellRow::depthAtPosition).setHeader("Depth").setWidth("5em").setFlexGrow(0);
        grid.addColumn(row -> MoneyDisplay.format(row.salaryWeekly(), currency)).setHeader("Wage/wk");
        grid.addColumn(row -> row.askingPrice() == null ? "" : MoneyDisplay.format(row.askingPrice(), currency))
                .setHeader("Asking");
        grid.addColumn(SquadAdvice.SellRow::contractEnd).setHeader("Contract");
        grid.addColumn(row -> String.join(", ", row.reasons())).setHeader("Reasons").setFlexGrow(1);
    }

    private void run() {
        String club = clubFilter.getValue();
        if (club == null || club.isBlank()) {
            Notification.show("Pick a club first", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        try {
            List<SquadAdvice.SellRow> rows = tools.sellRows(club);
            grid.setItems(rows);
            long sell = rows.stream().filter(row -> "sell".equals(row.recommendation())).count();
            long loan = rows.stream().filter(row -> "loan".equals(row.recommendation())).count();
            summary.setText(rows.size() + " players · " + sell + " sell · " + loan + " loan");
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
