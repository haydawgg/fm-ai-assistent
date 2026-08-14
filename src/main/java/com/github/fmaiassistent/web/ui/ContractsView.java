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
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Route(value = "contracts", layout = AppShell.class)
@PageTitle("Contracts")
@CssImport("./styles/moneyball-view.css")
@CssImport("./styles/pitch-board.css")
@CssImport(value = "./styles/player-grid.css", themeFor = "vaadin-grid")
public class ContractsView extends VerticalLayout {
    private final FmAiAssistentTools tools;
    private final String sessionClub;
    private final MoneyCurrency currency;
    private final Button runButton = new Button("Refresh queue", VaadinIcon.WALLET.create());
    private final Span summary = new Span();
    private final Span healthLabel = new Span();
    private final ProgressBar healthBar = new ProgressBar();
    private final Grid<SquadAdvice.ContractRow> grid = new Grid<>();

    public ContractsView(FmAiAssistentTools tools, ClubDatabaseService clubs, AppSettingsService settings) {
        this.tools = tools;
        this.currency = settings.currency();
        this.sessionClub = SessionClub.resolved(settings, SessionClub.names(clubs));
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("moneyball-view");
        configureGrid();
        healthLabel.addClassName("contracts-health-label");
        healthBar.setMin(0);
        healthBar.setMax(1);
        Div health = new Div(healthLabel, healthBar);
        health.addClassName("contracts-health");
        summary.addClassName("moneyball-summary");
        add(header(), filterBar(), health, summary, grid);
        expand(grid);
        if (clubs.findAllClubs().isEmpty()) {
            grid.setVisible(false);
            health.setVisible(false);
            summary.setText("Load from the top bar with FM26 running.");
            summary.addClassName("moneyball-empty");
            return;
        }
        if (sessionClub.isBlank()) {
            grid.setVisible(false);
            health.setVisible(false);
            summary.setText("Pick your club in the top bar.");
            summary.addClassName("moneyball-empty");
            return;
        }
        run();
    }

    private Component header() {
        Span hint = new Span("Renew, sell or loan anyone out of contract in 180 days. Wage bill vs payroll is the club health bar.");
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
        grid.setEmptyStateText("Nobody out of contract in the next 180 days.");
        grid.addItemClickListener(event -> PlayerDossier.openNamed(tools, event.getItem().name(), currency, sessionClub));
        grid.addColumn(SquadAdvice.ContractRow::action)
                .setHeader("Action")
                .setRenderer(new ComponentRenderer<>(row -> {
                    String action = row.action() == null ? "" : row.action();
                    Span badge = new Span(action);
                    badge.addClassName("row-badge");
                    if ("sell".equals(action)) {
                        badge.addClassName("row-badge-injury");
                    } else if ("loan".equals(action)) {
                        badge.addClassName("row-badge-loan");
                    } else {
                        badge.addClassName("row-badge-transfer");
                    }
                    return badge;
                }))
                .setWidth("7em")
                .setFlexGrow(0);
        grid.addColumn(SquadAdvice.ContractRow::name).setHeader("Name").setAutoWidth(true);
        grid.addColumn(SquadAdvice.ContractRow::position).setHeader("Pos").setWidth("4em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.ContractRow::age).setHeader("Age").setWidth("4em").setFlexGrow(0);
        grid.addColumn(SquadAdvice.ContractRow::ca).setHeader("CA").setWidth("4em").setFlexGrow(0);
        grid.addColumn(row -> MoneyDisplay.format(row.salaryWeekly(), currency)).setHeader("Wage/wk");
        grid.addColumn(SquadAdvice.ContractRow::contractEnd).setHeader("Contract");
        grid.addColumn(SquadAdvice.ContractRow::daysUntilExpiry).setHeader("Days").setWidth("5em").setFlexGrow(0);
        grid.addColumn(row -> String.join(", ", row.reasons())).setHeader("Reasons").setFlexGrow(1);
    }

    private void run() {
        if (sessionClub.isBlank()) {
            Notification.show("Pick your club in the top bar", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        UI ui = UI.getCurrent();
        runButton.setEnabled(false);
        CompletableFuture.supplyAsync(() -> new Result(
                        tools.contractRows(sessionClub),
                        tools.wageHealth(sessionClub)))
                .thenAccept(result -> ui.access(() -> {
                    grid.setItems(result.rows());
                    applyHealth(result.health());
                    long renew = result.rows().stream().filter(row -> "renew".equals(row.action())).count();
                    long sell = result.rows().stream().filter(row -> "sell".equals(row.action())).count();
                    long loan = result.rows().stream().filter(row -> "loan".equals(row.action())).count();
                    summary.removeClassName("moneyball-empty");
                    summary.setText(result.rows().size() + " contracts in 180 days · "
                            + renew + " renew · " + sell + " sell · " + loan + " loan");
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

    private void applyHealth(SquadAdvice.WageHealth health) {
        String bill = MoneyDisplay.format(health.wageBillWeekly(), currency);
        if (health.payrollBudget() == null || health.payrollBudget() <= 0) {
            healthLabel.setText("Wage bill " + bill + " /wk · payroll budget unknown");
            healthBar.setValue(0);
            healthBar.removeThemeVariants(ProgressBarVariant.LUMO_ERROR, ProgressBarVariant.LUMO_SUCCESS);
            return;
        }
        String payroll = MoneyDisplay.format(health.payrollBudget(), currency);
        double used = health.usedFraction() == null ? 0 : health.usedFraction();
        healthLabel.setText("Wage bill " + bill + " /wk vs payroll " + payroll
                + " (" + String.format(Locale.ROOT, "%.0f", used * 100) + "%)");
        healthBar.setValue(Math.min(1.0, Math.max(0.0, used)));
        healthBar.removeThemeVariants(ProgressBarVariant.LUMO_ERROR, ProgressBarVariant.LUMO_SUCCESS);
        if (health.overBudget()) {
            healthBar.addThemeVariants(ProgressBarVariant.LUMO_ERROR);
        } else {
            healthBar.addThemeVariants(ProgressBarVariant.LUMO_SUCCESS);
        }
    }

    private record Result(List<SquadAdvice.ContractRow> rows, SquadAdvice.WageHealth health) {
    }
}
