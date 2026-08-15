package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.service.AppSettingsService;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.github.fmaiassistent.service.OpenRouterModelCatalog;
import com.github.fmaiassistent.service.PlayerDatabaseService;
import com.github.fmaiassistent.service.RamLoadCoordinator;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.RouterLayout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@CssImport("./styles/fmai-dark.css")
@CssImport("./styles/chat-view.css")
@CssImport("./styles/pitch-board.css")
@CssImport(value = "./styles/chat-messages.css", themeFor = "vaadin-message")
public class AppShell extends AppLayout implements RouterLayout, AfterNavigationObserver {

    private final AppSettingsService settings;
    private final OpenRouterModelCatalog catalog;
    private final RamLoadCoordinator ramLoad;
    private final ClubDatabaseService clubs;
    private final PlayerDatabaseService players;
    private final Map<String, NavItem> navItems = new LinkedHashMap<>();
    private final Span pageTitle = new Span();
    private final Div contentWrapper = new Div();
    private final VerticalLayout sidebarNav = new VerticalLayout();
    private final Span snapshot = new Span();
    private final ComboBox<String> club = new ComboBox<>();
    private final Button loadButton = new Button("Load", VaadinIcon.DATABASE.create());
    private final Button settingsButton = new Button(VaadinIcon.COG.create());

    public AppShell(
            AppSettingsService settings,
            ClubDatabaseService clubs,
            PlayerDatabaseService players,
            RamLoadCoordinator ramLoad,
            OpenRouterModelCatalog catalog) {
        this.settings = settings;
        this.catalog = catalog;
        this.ramLoad = ramLoad;
        this.clubs = clubs;
        this.players = players;
        setPrimarySection(Section.DRAWER);
        addClassName("fmai-shell");

        buildSidebar();
        buildTopbar(clubs, players);

        contentWrapper.addClassName("fmai-content");
        setContent(contentWrapper);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        OnboardingWizard.openIfNeeded(settings, clubs, players, ramLoad, catalog);
    }

    @Override
    public void showRouterLayoutContent(HasElement content) {
        contentWrapper.removeAll();
        if (content != null) {
            content.getElement().getClassList().add("fmai-route");
            contentWrapper.add((Component) content);
        }
    }

    private void buildSidebar() {
        Div sidebar = new Div();
        sidebar.addClassName("fmai-sidebar");

        sidebarNav.addClassName("fmai-sidebar-nav");
        sidebarNav.setPadding(false);
        sidebarNav.setSpacing(false);

        addNavItem("Desk", VaadinIcon.GRID, "");
        addNavItem("Shortlist", VaadinIcon.SEARCH, "shortlist");
        addNavItem("Moneyball", VaadinIcon.TRENDING_UP, "moneyball");
        addNavItem("Squad trim", VaadinIcon.MINUS, "squad-trim");
        addNavItem("First XI", VaadinIcon.CLIPBOARD_TEXT, "first-xi");
        addNavItem("Contracts", VaadinIcon.WALLET, "contracts");
        addNavItem("Academy", VaadinIcon.ACADEMY_CAP, "academy");
        addNavItem("Compare", VaadinIcon.SPLIT, "compare-squads");
        addNavItem("Chat", VaadinIcon.CHAT, "chat");

        sidebar.add(sidebarNav);
        addToDrawer(sidebar);
    }

    private void buildTopbar(ClubDatabaseService clubs, PlayerDatabaseService players) {
        HorizontalLayout topbar = new HorizontalLayout();
        topbar.addClassName("fmai-topbar");
        topbar.setWidthFull();
        topbar.setAlignItems(FlexComponent.Alignment.CENTER);
        topbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        DrawerToggle drawerToggle = new DrawerToggle();
        drawerToggle.addClassName("fmai-sidebar-toggle");
        drawerToggle.getElement().setAttribute("aria-label", "Toggle sidebar");

        Div logo = new Div(new Span("FM"));
        logo.addClassName("fmai-sidebar-logo");
        logo.getElement().setAttribute("aria-hidden", "true");

        pageTitle.addClassName("fmai-topbar-title");

        HorizontalLayout left = new HorizontalLayout(drawerToggle, logo, pageTitle);
        left.setAlignItems(FlexComponent.Alignment.CENTER);
        left.setSpacing(true);

        snapshot.addClassName("fmai-snapshot");
        refreshSnapshot(players);
        snapshot.getElement().setAttribute("role", "button");
        snapshot.getStyle().set("cursor", "pointer");
        snapshot.addClickListener(event -> {
            if ("true".equals(snapshot.getElement().getAttribute("data-stale"))
                    || "true".equals(snapshot.getElement().getAttribute("data-empty"))) {
                RamLoadUi.start(ramLoad, loadButton);
            }
        });

        Span currency = new Span(settings.currency().symbol() + " " + settings.currency().label());
        currency.addClassName("fmai-currency");
        currency.getElement().setAttribute("title", "Display currency — change in Settings");

        List<String> names = SessionClub.names(clubs);
        club.setPlaceholder("Your club");
        club.setAriaLabel("Your club");
        club.setClearButtonVisible(true);
        club.addClassName("fmai-session-club");
        club.setWidth("14rem");
        SessionClub.prefill(club, settings, names);
        club.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                settings.saveSessionClub(event.getValue());
                UI ui = UI.getCurrent();
                if (ui != null) {
                    ui.getPage().reload();
                }
            }
        });

        loadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loadButton.addClassName("fmai-load");
        loadButton.getElement().setAttribute("aria-label", "Load from RAM");
        loadButton.addClickListener(event -> RamLoadUi.start(ramLoad, loadButton));

        settingsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        settingsButton.addClassName("icon-button");
        settingsButton.setTooltipText("Settings");
        settingsButton.getElement().setAttribute("aria-label", "Settings");
        settingsButton.addClickListener(event -> SettingsDialog.open(
                settings, catalog, settings.currency(), ignored -> {
                    UI ui = UI.getCurrent();
                    if (ui != null) {
                        ui.getPage().reload();
                    }
                }));

        HorizontalLayout actions = new HorizontalLayout(snapshot, currency, club, loadButton, settingsButton);
        actions.setAlignItems(FlexComponent.Alignment.CENTER);
        actions.setSpacing(true);
        actions.addClassName("fmai-topbar-actions");

        topbar.add(left, actions);
        addToNavbar(topbar);
    }

    private void refreshSnapshot(PlayerDatabaseService players) {
        SnapshotHeartbeat.Status status = SnapshotHeartbeat.from(players.metadata(), players.countPlayers());
        snapshot.setText(status.label());
        snapshot.getElement().setAttribute("title", status.title());
        snapshot.getElement().setAttribute("data-empty", status.empty());
        snapshot.getElement().setAttribute("data-stale", status.stale());
    }

    private void addNavItem(String label, VaadinIcon icon, String route) {
        Span caption = new Span(label);
        caption.addClassName("fmai-nav-label");
        Button item = new Button();
        item.setIcon(icon.create());
        item.setSuffixComponent(caption);
        item.addClassName("fmai-nav-item");
        item.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        item.addClickListener(e -> UI.getCurrent().navigate(route));
        item.getElement().setAttribute("data-route", route);
        navItems.put(route, new NavItem(label, item));
        sidebarNav.add(item);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        String route = event.getLocation().getPath();
        if (route.isEmpty()) {
            route = "";
        }
        updateActiveNav(route);
        updatePageTitle(route);
        NavItem item = navItems.get(route.isEmpty() ? "" : route);
        ChatUiContext.setView(item == null ? "Desk" : item.label());
    }

    private void updateActiveNav(String currentRoute) {
        for (Map.Entry<String, NavItem> entry : navItems.entrySet()) {
            boolean active = entry.getKey().equals(currentRoute);
            entry.getValue().button().getElement().setAttribute("active", active);
            if (active) {
                entry.getValue().button().addClassName("active");
            } else {
                entry.getValue().button().removeClassName("active");
            }
        }
    }

    private void updatePageTitle(String route) {
        NavItem item = navItems.get(route);
        pageTitle.setText(item != null ? item.label() : "FM AI Assistent");
    }

    private record NavItem(String label, Button button) {
    }
}
