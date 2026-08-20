package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.service.AppSettingsService;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.github.fmaiassistent.service.OpenRouterModelCatalog;
import com.github.fmaiassistent.service.PlayerDatabaseService;
import com.github.fmaiassistent.service.RamLoadCoordinator;
import com.github.fmaiassistent.service.DemoDataService;
import com.github.fmaiassistent.football.PlayerAnalysisPort;
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
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
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
    private final DemoDataService demoData;
    private final PlayerAnalysisPort tools;
    private final GlobalSearchService search;
    private final Map<String, NavItem> navItems = new LinkedHashMap<>();
    private final Span pageTitle = new Span();
    private final Div contentWrapper = new Div();
    private final VerticalLayout sidebarNav = new VerticalLayout();
    private final Span snapshot = new Span();
    private final ComboBox<String> club = new ComboBox<>();
    private final Button loadButton = new Button("Refresh from FM", VaadinIcon.DATABASE.create());
    private final Button settingsButton = new Button(VaadinIcon.COG.create());
    private final Button globalSearchButton = new Button(VaadinIcon.SEARCH.create());
    private final TextField globalSearch = new TextField();

    public AppShell(
            AppSettingsService settings,
            ClubDatabaseService clubs,
            PlayerDatabaseService players,
            RamLoadCoordinator ramLoad,
            OpenRouterModelCatalog catalog,
            DemoDataService demoData,
            PlayerAnalysisPort tools,
            GlobalSearchService search) {
        this.settings = settings;
        this.catalog = catalog;
        this.ramLoad = ramLoad;
        this.clubs = clubs;
        this.players = players;
        this.demoData = demoData;
        this.tools = tools;
        this.search = search;
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
        getElement().executeJs("""
                document.addEventListener('keydown', function(event) {
                    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
                        event.preventDefault();
                        const field = document.querySelector('[data-global-search]');
                        if (field) { field.focus(); }
                    }
                });
                """);
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

        addNavSection("Decide");
        addNavItem("Overview", VaadinIcon.DASHBOARD, "");
        addNavItem("First XI", VaadinIcon.CLIPBOARD_TEXT, "first-xi");
        addNavItem("Compare", VaadinIcon.SPLIT, "compare-squads");
        addNavSection("Scout");
        addNavItem("Shortlist", VaadinIcon.SEARCH, "shortlist");
        addNavItem("Player desk", VaadinIcon.USERS, "desk");
        addNavItem("Moneyball", VaadinIcon.TRENDING_UP, "moneyball");
        addNavSection("Manage");
        addNavItem("Contracts", VaadinIcon.WALLET, "contracts");
        addNavItem("Squad trim", VaadinIcon.MINUS, "squad-trim");
        addNavItem("Academy", VaadinIcon.ACADEMY_CAP, "academy");
        addNavSection("Ask");
        addNavItem("Chat", VaadinIcon.CHAT, "chat");

        sidebar.add(sidebarNav);
        sidebar.add(sidebarStatus());
        addToDrawer(sidebar);
    }

    private Component sidebarStatus() {
        Div status = new Div();
        status.addClassName("fmai-sidebar-status");
        Span sourceLabel = new Span("DATA SOURCE");
        sourceLabel.addClassName("fmai-sidebar-status-label");
        Span source = new Span(demoData.enabled()
                ? "Demo snapshot — not FM26"
                : players.countPlayers() > 0 ? "FM26 snapshot connected" : "Awaiting FM26 snapshot");
        source.addClassName("fmai-sidebar-status-value");
        Span apiLabel = new Span("AI API");
        apiLabel.addClassName("fmai-sidebar-status-label");
        Span api = new Span(settings.openRouterApiKey().isBlank() ? "Setup required" : "OpenRouter ready");
        api.addClassName("fmai-sidebar-status-value");
        status.add(sourceLabel, source, apiLabel, api);
        return status;
    }

    private void addNavSection(String label) {
        Span heading = new Span(label);
        heading.addClassName("fmai-nav-section");
        sidebarNav.add(heading);
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

        globalSearch.setPlaceholder("Search players, clubs…");
        globalSearch.setPrefixComponent(VaadinIcon.SEARCH.create());
        globalSearch.setClearButtonVisible(true);
        globalSearch.setValueChangeMode(ValueChangeMode.EAGER);
        globalSearch.addClassName("fmai-global-search");
        globalSearch.getElement().setAttribute("aria-label", "Search players and clubs");
        globalSearch.getElement().setAttribute("data-global-search", "true");
        globalSearch.addKeyPressListener(com.vaadin.flow.component.Key.ENTER, event -> openGlobalSearch(globalSearch.getValue()));
        globalSearchButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        globalSearchButton.addClassName("icon-button");
        globalSearchButton.setTooltipText("Search players and clubs");
        globalSearchButton.getElement().setAttribute("aria-label", "Open search");
        globalSearchButton.addClickListener(event -> openGlobalSearch(globalSearch.getValue()));

        HorizontalLayout left = new HorizontalLayout(drawerToggle, logo, pageTitle);
        left.setAlignItems(FlexComponent.Alignment.CENTER);
        left.setSpacing(true);

        snapshot.addClassName("fmai-snapshot");
        refreshSnapshot(players);
        snapshot.getElement().setAttribute("role", "button");
        snapshot.getElement().setAttribute("tabindex", "0");
        snapshot.getElement().setAttribute("aria-label", "Snapshot status. Activate to load from RAM when the snapshot is stale or empty.");
        snapshot.getStyle().set("cursor", "pointer");
        snapshot.getElement().executeJs("this.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); this.click(); } });");
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
        loadButton.getElement().setAttribute("aria-label", "Refresh snapshot from FM26 memory");
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

        Span demo = new Span("DEMO DATA — NOT FROM FM26");
        demo.addClassName("fmai-demo-badge");
        demo.setVisible(demoData.enabled());
        HorizontalLayout actions = new HorizontalLayout(demo, snapshot, globalSearch, globalSearchButton, currency, club, loadButton, settingsButton);
        actions.setAlignItems(FlexComponent.Alignment.CENTER);
        actions.setSpacing(true);
        actions.addClassName("fmai-topbar-actions");

        topbar.add(left, actions);
        addToNavbar(topbar);
    }

    private void openGlobalSearch(String query) {
        List<GlobalSearchService.Result> results = search.search(query);
        Dialog dialog = new Dialog();
        dialog.addClassName("global-search-dialog");
        dialog.setHeaderTitle("Search command center");
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(false);
        if (results.isEmpty()) {
            Span empty = new Span(query == null || query.isBlank() ? "Type at least two characters." : "No players or clubs matched that search.");
            empty.addClassName("global-search-empty");
            empty.getElement().setAttribute("role", "status");
            empty.getElement().setAttribute("aria-live", "polite");
            content.add(empty);
        } else {
            addSearchGroup(content, results, GlobalSearchService.Kind.PLAYER, "Players", dialog);
            addSearchGroup(content, results, GlobalSearchService.Kind.CLUB, "Clubs", dialog);
        }
        dialog.add(content);
        dialog.setWidth("520px");
        dialog.open();
    }

    private void addSearchGroup(
            VerticalLayout content,
            List<GlobalSearchService.Result> results,
            GlobalSearchService.Kind kind,
            String title,
            Dialog dialog) {
        List<GlobalSearchService.Result> group = results.stream().filter(result -> result.kind() == kind).toList();
        if (group.isEmpty()) {
            return;
        }
        H3 heading = new H3(title);
        heading.addClassName("global-search-heading");
        content.add(heading);
        for (GlobalSearchService.Result result : group) {
            Button item = new Button(result.name(), VaadinIcon.ARROW_RIGHT.create());
            item.addClassName("global-search-result");
            item.setTooltipText(result.secondary());
            item.getElement().setAttribute("aria-label", result.secondary().isBlank()
                    ? result.name()
                    : result.name() + " — " + result.secondary());
            item.addClickListener(event -> {
                dialog.close();
                if (result.kind() == GlobalSearchService.Kind.PLAYER) {
                    PlayerDossier.openNamed(tools, result.playerName(), settings.currency(), SessionClub.resolved(settings, SessionClub.names(clubs)));
                } else {
                    settings.saveSessionClub(result.name());
                    UI ui = UI.getCurrent();
                    if (ui != null) {
                        ui.getPage().reload();
                    }
                }
            });
            content.add(item);
        }
    }

    private void refreshSnapshot(PlayerDatabaseService players) {
        SnapshotStatusModel status = SnapshotStatusModel.from(players.metadata(), players.countPlayers());
        snapshot.setText(status.state() == WorkspaceLoadState.READY
                ? status.playerCount() + " players · " + (status.season().isBlank() ? "snapshot ready" : status.season())
                : status.state() == WorkspaceLoadState.PARTIAL ? "Partial snapshot · stats unknown" : status.label());
        snapshot.getElement().setAttribute("title", status.detail());
        snapshot.getElement().setAttribute("data-empty", Boolean.toString(status.state() == WorkspaceLoadState.NO_SNAPSHOT));
        snapshot.getElement().setAttribute("data-stale", Boolean.toString(status.state() == WorkspaceLoadState.STALE));
        snapshot.getElement().setAttribute("data-state", status.state().name());
    }

    private void addNavItem(String label, VaadinIcon icon, String route) {
        Span caption = new Span(label);
        caption.addClassName("fmai-nav-label");
        Button item = new Button();
        item.setIcon(icon.create());
        item.setSuffixComponent(caption);
        item.addClassName("fmai-nav-item");
        item.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        item.getElement().setAttribute("aria-label", label);
        item.getElement().setAttribute("title", label);
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
            entry.getValue().button().getElement().setAttribute("aria-current", active ? "page" : "false");
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
