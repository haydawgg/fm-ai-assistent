package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import com.github.fmaiassistent.football.PlayerAnalysisPort;
import com.github.fmaiassistent.football.SquadAdvicePort;
import com.github.fmaiassistent.football.SquadSellCandidate;
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
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Route(value = "squad-trim", layout = AppShell.class)
@PageTitle("Squad trim")
@CssImport("./styles/moneyball-view.css")
@CssImport(value = "./styles/player-grid.css", themeFor = "vaadin-grid")
public class SquadTrimView extends VerticalLayout {
    private final PlayerAnalysisPort dossierTools;
    private final SquadAdvicePort advice;
    private final String sessionClub;
    private final Button runButton = new Button("Rank squad", VaadinIcon.MINUS.create());
    private final Span summary = new Span();
    private final Grid<SquadSellCandidate> grid = new Grid<>();
    private final MoneyCurrency currency;

    public SquadTrimView(PlayerAnalysisPort tools, ClubDatabaseService clubs, AppSettingsService settings) {
        this(tools, requireSquadAdvicePort(tools), clubs, settings);
    }

    @Autowired
    public SquadTrimView(
            PlayerAnalysisPort dossierTools,
            SquadAdvicePort advice,
            ClubDatabaseService clubs,
            AppSettingsService settings) {
        this.dossierTools = dossierTools;
        this.advice = advice;
        this.currency = settings.currency();
        this.sessionClub = SessionClub.resolved(settings, SessionClub.names(clubs));
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("moneyball-view");
        configureGrid();
        add(header(), filterBar(), summary, grid);
        expand(grid);
        summary.addClassName("moneyball-summary");
        summary.getElement().setAttribute("role", "status");
        summary.getElement().setAttribute("aria-live", "polite");
        if (!clubs.hasClubs()) {
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
        return new WorkspaceHeader("Squad trim",
                "Sell, loan or keep using depth, first-team CA, wages and contracts — the same ranking as fm26_sell_shortlist.");
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
        grid.setEmptyStateText("Pick your club in the top bar to rank the squad.");
        grid.addItemClickListener(event -> {
            if (event.getColumn() != null && "ask".equals(event.getColumn().getKey())) {
                return;
            }
            PlayerDossier.openNamed(dossierTools, event.getItem().name(), currency, sessionClub);
        });
        grid.addComponentColumn(row -> ChatLaunch.askButton(row.name(), sessionClub))
                .setHeader("")
                .setKey("ask")
                .setWidth("4.5em")
                .setFlexGrow(0);
        grid.addColumn(SquadSellCandidate::rank).setHeader("Rank").setWidth("5.5em").setFlexGrow(0);
        grid.addColumn(SquadSellCandidate::recommendation)
                .setHeader("Call")
                .setRenderer(new ComponentRenderer<>(row -> {
                    String call = row.recommendation() == null ? "" : row.recommendation();
                    Span badge = new Span(call);
                    badge.addClassName("row-badge");
                    if ("sell".equals(call)) {
                        badge.addClassName("row-badge-injury");
                    } else if ("loan".equals(call)) {
                        badge.addClassName("row-badge-loan");
                    } else {
                        badge.addClassName("row-badge-transfer");
                    }
                    return badge;
                }))
                .setWidth("7em")
                .setFlexGrow(0);
        grid.addColumn(SquadSellCandidate::name).setHeader("Name").setWidth("16em").setFlexGrow(0);
        grid.addColumn(SquadSellCandidate::position).setHeader("Pos").setWidth("6em").setFlexGrow(0);
        grid.addColumn(SquadSellCandidate::age).setHeader("Age").setWidth("5em").setFlexGrow(0);
        grid.addColumn(SquadSellCandidate::ca).setHeader("CA").setWidth("5em").setFlexGrow(0);
        grid.addColumn(SquadSellCandidate::pa).setHeader("PA").setWidth("5em").setFlexGrow(0);
        grid.addColumn(SquadSellCandidate::caVsFirstTeam).setHeader("vs XI").setWidth("7em").setFlexGrow(0);
        grid.addColumn(SquadSellCandidate::depthAtPosition).setHeader("Depth").setWidth("7em").setFlexGrow(0);
        grid.addColumn(row -> MoneyDisplay.format(row.salaryWeekly(), currency))
                .setHeader("Wage/wk")
                .setWidth("10em")
                .setFlexGrow(0);
        grid.addColumn(row -> row.askingPrice() == null ? "" : MoneyDisplay.format(row.askingPrice(), currency))
                .setHeader("Asking")
                .setWidth("10em")
                .setFlexGrow(0);
        grid.addColumn(SquadSellCandidate::contractEnd)
                .setHeader("Contract")
                .setWidth("10em")
                .setFlexGrow(0);
        grid.addColumn(row -> String.join(", ", row.reasons()))
                .setHeader("Reasons")
                .setWidth("20em")
                .setFlexGrow(0);
    }

    private void run() {
        String club = sessionClub;
        if (club.isBlank()) {
            UiFeedback.show("Pick your club in the top bar", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        UI ui = UI.getCurrent();
        runButton.setEnabled(false);
        summary.removeClassName("moneyball-empty");
        summary.setText("Ranking squad…");
        summary.getElement().setAttribute("aria-busy", "true");
        UiAsync.submit(ui, () -> advice.squadSellCandidates(club), rows -> {
                    grid.setItems(rows);
                    long sell = rows.stream().filter(row -> "sell".equals(row.recommendation())).count();
                    long loan = rows.stream().filter(row -> "loan".equals(row.recommendation())).count();
                    summary.setText(rows.size() + " players · " + sell + " sell · " + loan + " loan");
                    summary.getElement().setAttribute("aria-busy", "false");
                    runButton.setEnabled(true);
                }, ex -> {
                    UiFeedback.error(ex, "Squad ranking failed — try again.");
                    summary.setText("Squad ranking failed — try again.");
                    summary.getElement().setAttribute("aria-busy", "false");
                    runButton.setEnabled(true);
                });
    }

    private static SquadAdvicePort requireSquadAdvicePort(PlayerAnalysisPort tools) {
        if (tools instanceof SquadAdvicePort port) {
            return port;
        }
        throw new IllegalArgumentException("Player analysis adapter does not provide squad advice queries");
    }
}
