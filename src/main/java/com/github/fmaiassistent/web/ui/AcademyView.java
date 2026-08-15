package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import com.github.fmaiassistent.mcp.FmAiAssistentTools;
import com.github.fmaiassistent.mcp.SquadAdvice;
import com.github.fmaiassistent.service.AppSettingsService;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Route(value = "academy", layout = AppShell.class)
@PageTitle("Academy")
@CssImport("./styles/moneyball-view.css")
@CssImport(value = "./styles/player-grid.css", themeFor = "vaadin-grid")
public class AcademyView extends VerticalLayout {
    private final FmAiAssistentTools tools;
    private final String sessionClub;
    private final MoneyCurrency currency;
    private final IntegerField maxAge = new IntegerField("Max age");
    private final Button runButton = new Button("Show intake", VaadinIcon.ACADEMY_CAP.create());
    private final Span summary = new Span();
    private final Grid<SquadAdvice.AcademyRow> grid = new Grid<>();

    public AcademyView(FmAiAssistentTools tools, ClubDatabaseService clubs, AppSettingsService settings) {
        this.tools = tools;
        this.currency = settings.currency();
        this.sessionClub = SessionClub.resolved(settings, SessionClub.names(clubs));
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("moneyball-view");
        maxAge.setValue(21);
        maxAge.setMin(15);
        maxAge.setMax(23);
        configureGrid();
        summary.addClassName("moneyball-summary");
        add(header(), filterBar(), summary, grid);
        expand(grid);
        if (!clubs.hasClubs()) {
            grid.setVisible(false);
            summary.setText("Load from the top bar with FM26 running.");
            summary.addClassName("moneyball-empty");
            return;
        }
        if (sessionClub.isBlank()) {
            grid.setVisible(false);
            summary.setText("Pick your club in the top bar.");
            summary.addClassName("moneyball-empty");
            return;
        }
        run();
    }

    private Component header() {
        Span hint = new Span("Your club's U21s vs first-team CA. Dual positions are natural ratings (15+). This is your intake, not a world wonderkid shop.");
        hint.addClassName("moneyball-hint");
        hint.setWidthFull();
        return hint;
    }

    private HorizontalLayout filterBar() {
        maxAge.setWidth("8em");
        runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        runButton.addClickListener(event -> run());
        HorizontalLayout bar = new HorizontalLayout(maxAge, runButton);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.END);
        bar.addClassName("moneyball-filters");
        return bar;
    }

    private void configureGrid() {
        grid.addClassName("moneyball-grid");
        grid.setEmptyStateText("No youth players at this age cap.");
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
        grid.addColumn(SquadAdvice.AcademyRow::name).setHeader("Name").setAutoWidth(true);
        grid.addColumn(SquadAdvice.AcademyRow::position).setHeader("Pos").setWidth("4em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.AcademyRow::age).setHeader("Age").setWidth("4em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.AcademyRow::ca).setHeader("CA").setWidth("4em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.AcademyRow::pa).setHeader("PA").setWidth("4em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.AcademyRow::upside).setHeader("Upside").setWidth("5em").setFlexGrow(0);
        grid.addColumn(row -> row.vsFirstTeam() >= 0 ? "+" + row.vsFirstTeam() : String.valueOf(row.vsFirstTeam()))
                .setHeader("vs XI").setWidth("5em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.AcademyRow::dualPositions).setHeader("Natural pos").setWidth("7em").setFlexGrow(0);
        grid.addColumn(row -> MoneyDisplay.format(row.salaryWeekly(), currency)).setHeader("Wage/wk");
        grid.addColumn(SquadAdvice.AcademyRow::contractEnd).setHeader("Contract");
    }

    private void run() {
        if (sessionClub.isBlank()) {
            Notification.show("Pick your club in the top bar", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        Integer cap = maxAge.getValue() == null ? 21 : maxAge.getValue();
        UI ui = UI.getCurrent();
        runButton.setEnabled(false);
        CompletableFuture.supplyAsync(() -> tools.academyRows(sessionClub, cap))
                .thenAccept(rows -> ui.access(() -> {
                    grid.setItems(rows);
                    long cover = rows.stream().filter(row -> row.vsFirstTeam() >= -8).count();
                    summary.removeClassName("moneyball-empty");
                    summary.setText(rows.size() + " players ≤" + cap
                            + " · " + cover + " within 8 CA of the first-team average");
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
}
