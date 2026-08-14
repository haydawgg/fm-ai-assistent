package com.github.fmaiassistent.web.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.RouterLayout;

import java.util.LinkedHashMap;
import java.util.Map;

@CssImport("./styles/fmai-dark.css")
public class AppShell extends AppLayout implements RouterLayout, AfterNavigationObserver {

    private final Map<String, NavItem> navItems = new LinkedHashMap<>();
    private final Span pageTitle = new Span();
    private final Div contentWrapper = new Div();
    private final VerticalLayout sidebarNav = new VerticalLayout();
    private final Button collapseButton = new Button(VaadinIcon.MENU.create());
    private boolean collapsed = false;

    public AppShell() {
        setPrimarySection(Section.DRAWER);
        addClassName("fmai-shell");

        buildSidebar();
        buildTopbar();

        contentWrapper.addClassName("fmai-content");
        setContent(contentWrapper);
    }

    private void buildSidebar() {
        Div sidebar = new Div();
        sidebar.addClassName("fmai-sidebar");

        HorizontalLayout header = new HorizontalLayout();
        header.addClassName("fmai-sidebar-header");
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        Div logo = new Div(new Span("FM"));
        logo.addClassName("fmai-sidebar-logo");
        Span title = new Span("FM AI Assistent");
        title.addClassName("fmai-sidebar-title");

        collapseButton.addClassName("fmai-sidebar-toggle");
        collapseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        collapseButton.getElement().setAttribute("aria-label", "Toggle sidebar");
        collapseButton.getElement().setAttribute("aria-expanded", "true");
        collapseButton.addClickListener(e -> toggleSidebar());

        header.add(logo, title, collapseButton);
        header.setFlexGrow(1, title);

        sidebarNav.addClassName("fmai-sidebar-nav");
        sidebarNav.setPadding(false);
        sidebarNav.setSpacing(false);

        addNavItem("Desk", VaadinIcon.GRID, "");
        addNavItem("Shortlist", VaadinIcon.SEARCH, "shortlist");
        addNavItem("Moneyball", VaadinIcon.TRENDING_UP, "moneyball");
        addNavItem("Squad trim", VaadinIcon.MINUS, "squad-trim");
        addNavItem("First XI", VaadinIcon.CLIPBOARD_TEXT, "first-xi");
        addNavItem("Compare", VaadinIcon.SPLIT, "compare-squads");
        addNavItem("Chat", VaadinIcon.CHAT, "chat");

        sidebar.add(header, sidebarNav);
        addToDrawer(sidebar);
    }

    private void buildTopbar() {
        HorizontalLayout topbar = new HorizontalLayout();
        topbar.addClassName("fmai-topbar");
        topbar.setWidthFull();
        topbar.setAlignItems(FlexComponent.Alignment.CENTER);
        topbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        DrawerToggle drawerToggle = new DrawerToggle();
        drawerToggle.addClassName("fmai-sidebar-toggle");

        pageTitle.addClassName("fmai-topbar-title");

        HorizontalLayout left = new HorizontalLayout(drawerToggle, pageTitle);
        left.setAlignItems(FlexComponent.Alignment.CENTER);
        left.setSpacing(true);

        topbar.add(left);
        addToNavbar(topbar);
    }

    private void addNavItem(String label, VaadinIcon icon, String route) {
        Button item = new Button(label, icon.create());
        item.addClassName("fmai-nav-item");
        item.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        item.addClickListener(e -> UI.getCurrent().navigate(route));
        item.getElement().setAttribute("data-route", route);
        navItems.put(route, new NavItem(label, item));
        sidebarNav.add(item);
    }

    private void toggleSidebar() {
        collapsed = !collapsed;
        collapseButton.getElement().setAttribute("aria-expanded", String.valueOf(!collapsed));
        getElement().executeJs(
                "const sidebar = this.querySelector('.fmai-sidebar');" +
                "if (sidebar) sidebar.classList.toggle('collapsed', $0);",
                collapsed
        );
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        String route = event.getLocation().getPath();
        if (route.isEmpty()) {
            route = "";
        }
        updateActiveNav(route);
        updatePageTitle(route);
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
