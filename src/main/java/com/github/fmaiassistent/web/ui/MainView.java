package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.ClubEntity;
import com.github.fmaiassistent.domain.entity.CompetitionEntity;
import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.service.*;
import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import com.github.fmaiassistent.repository.*;
import com.github.fmaiassistent.player.AttributeDefinitions;
import com.github.fmaiassistent.player.FieldDef;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.*;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Route(value = "", layout = AppShell.class)
@PageTitle("FM AI Assistent")
@CssImport("./styles/main-view.css")
@CssImport("./styles/chat-view.css")
@CssImport(value = "./styles/chat-messages.css", themeFor = "vaadin-message")
@CssImport(value = "./styles/player-grid.css", themeFor = "vaadin-grid")
public class MainView extends VerticalLayout {
    private static final Logger LOGGER = LoggerFactory.getLogger(MainView.class);
    private static final Set<String> NUMERIC_SORT_COLUMNS = Set.of(
            "ID", "CLUB_ID", "PLAYING_CLUB_ID", "CURRENT_REPUTATION", "HOME_REPUTATION", "WORLD_REPUTATION",
            "CA", "PA", "ASKING_PRICE", "ASKING_PRICE_RAW", "SALARY_PA", "SALARY_WEEKLY_RAW", "AGE", "HEIGHT_CM",
            "REPUTATION", "TRANSFER_BUDGET", "PAYROLL_BUDGET",
            "GOALKEEPER", "DEFENDER_LEFT", "DEFENDER_CENTRAL", "DEFENDER_RIGHT", "WING_BACK_LEFT",
            "DEFENSIVE_MIDFIELDER", "WING_BACK_RIGHT", "MIDFIELDER_LEFT", "MIDFIELDER_CENTRAL",
            "MIDFIELDER_RIGHT", "ATTACKING_MIDFIELDER_LEFT", "ATTACKING_MIDFIELDER_CENTRAL",
            "ATTACKING_MIDFIELDER_RIGHT", "STRIKER",
            "CROSSING", "DRIBBLING", "FINISHING", "HEADING", "LONG_SHOTS", "MARKING", "OFF_THE_BALL",
            "PASSING", "PENALTIES", "TACKLING", "VISION", "HANDLING", "AERIAL_ABILITY", "COMMAND_OF_AREA",
            "COMMUNICATION", "KICKING", "THROWING", "ANTICIPATION", "DECISIONS", "ONE_ON_ONES",
            "POSITIONING", "REFLEXES", "FIRST_TOUCH", "TECHNIQUE", "LEFT_FOOT", "RIGHT_FOOT", "FLAIR",
            "CORNERS", "TEAMWORK", "WORK_RATE", "LONG_THROWS", "ECCENTRICITY", "RUSHING_OUT",
            "TENDENCY_TO_PUNCH", "ACCELERATION", "FREE_KICKS", "STRENGTH", "STAMINA", "PACE",
            "JUMPING_REACH", "LEADERSHIP", "DIRTINESS", "BALANCE", "BRAVERY", "CONSISTENCY",
            "AGGRESSION", "AGILITY", "IMPORTANT_MATCHES", "INJURY_PRONENESS", "VERSATILITY",
            "NATURAL_FITNESS", "DETERMINATION", "COMPOSURE", "CONCENTRATION");
    private static final Set<String> MONEY_COLUMNS = Set.of(
            "ASKING_PRICE", "ASKING_PRICE_RAW", "SALARY_PA", "SALARY_WEEKLY_RAW",
            "BALANCE", "TRANSFER_BUDGET", "PAYROLL_BUDGET");
    private static final Set<String> DEFAULT_PLAYER_COLUMN_KEYS = Set.of(
            "NAME", "AGE", "CLUB", "POSITION", "CA", "PA",
            "SALARY_WEEKLY_RAW", "ASKING_PRICE", "CONTRACT_END_DATE");

    private final PlayerDatabaseService players;
    private final ClubDatabaseService clubs;
    private final CompetitionDatabaseService competitions;
    private final AppSettingsService settings;

    private final Button filterButton = new Button("Filter", VaadinIcon.FILTER.create());
    private final Button columnsButton = new Button("All columns", VaadinIcon.GRID.create());
    private final ComboBox<String> savedViews = new ComboBox<>();
    private final Button saveViewButton = new Button("Save view", VaadinIcon.PLUS.create());
    private final Button deleteViewButton = new Button(VaadinIcon.TRASH.create());
    private final Div status = new Div();
    private final Tabs tabs = new Tabs();
    private final Div content = new Div();
    private final Grid<PlayerEntity> playersGrid = new Grid<>();
    private final Grid<ClubEntity> clubsGrid = new Grid<>();
    private final Grid<CompetitionEntity> competitionsGrid = new Grid<>();

    private final Tab playersTab = new Tab("Players");
    private final Tab clubsTab = new Tab("Clubs");
    private final Tab competitionsTab = new Tab("Competitions");
    private final TextField quickName = new TextField();
    private final ComboBox<String> quickClub = new ComboBox<>();
    private final IntegerField quickCaMin = new IntegerField();
    private final IntegerField quickAgeMax = new IntegerField();
    private PlayerFilterCriteria playerFilter = PlayerFilterCriteria.empty();
    private ClubFilterCriteria clubFilter = ClubFilterCriteria.empty();
    private CompetitionFilterCriteria competitionFilter = CompetitionFilterCriteria.empty();
    private MoneyCurrency currency;
    private boolean showAllPlayerColumns;
    private PlayerEntity selectedPlayer;
    private PlayerEntity compareAnchor;
    private boolean awaitingCompareSelection;
    private boolean syncingQuickFilters;
    private boolean syncingSavedViews;
    private boolean playersColumnsBuilt;
    private boolean playersColumnsAllMode;
    private boolean clubsColumnsBuilt;
    private boolean competitionsColumnsBuilt;
    private List<PlayerEntity> visiblePlayers = List.of();

    public MainView(
            PlayerDatabaseService players,
            ClubDatabaseService clubs,
            CompetitionDatabaseService competitions,
            AppSettingsService settings) {
        this.players = players;
        this.clubs = clubs;
        this.competitions = competitions;
        this.settings = settings;
        this.currency = settings.currency();

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("main-view");
        content.addClassName("workspace-content");

        add(header(), content);
        expand(content);
        configureTabs();
        configureGrid(playersGrid);
        configureGrid(clubsGrid);
        configureGrid(competitionsGrid);
        configureQuickFilters();
        configurePlayerShortcuts();
        playersGrid.addClassName("players-grid");
        playersGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
        playersGrid.addSelectionListener(event -> {
            if (!event.isFromClient()) {
                return;
            }
            event.getFirstSelectedItem().ifPresentOrElse(this::openPlayerDrawer, this::closePlayerDrawer);
        });
        updateStatus(null);
        showPlayers();
    }

    private Component header() {
        filterButton.addClickListener(event -> openFilterDialog());
        filterButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        filterButton.addClassName("toolbar-button");

        status.addClassName("app-status");
        HorizontalLayout appBar = new HorizontalLayout(status);
        appBar.setWidthFull();
        appBar.setAlignItems(Alignment.CENTER);
        appBar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        appBar.setPadding(false);
        appBar.setSpacing(true);
        appBar.addClassName("app-bar");
        appBar.addClassName("app-actions");

        columnsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        columnsButton.addClassName("toolbar-button");
        columnsButton.addClickListener(event -> {
            showAllPlayerColumns = !showAllPlayerColumns;
            columnsButton.setText(showAllPlayerColumns ? "Key columns" : "All columns");
            if (tabs.getSelectedTab() == playersTab) {
                showPlayers();
            }
        });
        savedViews.setPlaceholder("Saved views");
        savedViews.setClearButtonVisible(true);
        savedViews.setWidth("180px");
        savedViews.addClassName("saved-views");
        savedViews.addValueChangeListener(event -> {
            if (!syncingSavedViews && event.getValue() != null) {
                applySavedView(event.getValue());
            }
        });
        saveViewButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        saveViewButton.addClassName("toolbar-button");
        saveViewButton.addClickListener(event -> openSaveViewDialog());
        deleteViewButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        deleteViewButton.addClassName("toolbar-button");
        deleteViewButton.addClassName("toolbar-button-danger");
        deleteViewButton.setTooltipText("Delete selected view");
        deleteViewButton.getElement().setAttribute("aria-label", "Delete selected view");
        deleteViewButton.addClickListener(event -> deleteSelectedView());
        refreshSavedViewOptions();

        HorizontalLayout navigation = new HorizontalLayout(
                tabs, savedViews, saveViewButton, deleteViewButton, columnsButton, filterButton);
        navigation.setWidthFull();
        navigation.setAlignItems(Alignment.CENTER);
        navigation.expand(tabs);
        navigation.setPadding(false);
        navigation.setSpacing(true);
        navigation.setWrap(true);
        navigation.addClassName("workspace-nav");
        columnsButton.setVisible(true);
        savedViews.setVisible(true);
        saveViewButton.setVisible(true);
        deleteViewButton.setVisible(true);

        Div header = new Div(appBar, navigation);
        header.addClassName("app-header");
        return header;
    }

    private void configureTabs() {
        tabs.add(playersTab, clubsTab, competitionsTab);
        tabs.setWidthFull();
        tabs.addClassName("workspace-tabs");
        playersTab.addComponentAsFirst(VaadinIcon.USERS.create());
        clubsTab.addComponentAsFirst(VaadinIcon.OFFICE.create());
        competitionsTab.addComponentAsFirst(VaadinIcon.TROPHY.create());
        tabs.addSelectedChangeListener(event -> {
            boolean playersSelected = event.getSelectedTab() == playersTab;
            columnsButton.setVisible(playersSelected);
            savedViews.setVisible(playersSelected);
            saveViewButton.setVisible(playersSelected);
            deleteViewButton.setVisible(playersSelected);
            filterButton.setVisible(playersSelected
                    || event.getSelectedTab() == clubsTab
                    || event.getSelectedTab() == competitionsTab);
            if (!playersSelected) {
                selectedPlayer = null;
                clearCompareState();
            }
            if (playersSelected) {
                showPlayers();
            } else if (event.getSelectedTab() == clubsTab) {
                showClubs();
            } else {
                showCompetitions();
            }
        });
    }

    private void configureQuickFilters() {
        quickName.setPlaceholder("Name");
        quickName.getElement().setAttribute("aria-label", "Name");
        quickName.setClearButtonVisible(true);
        quickName.setWidth("160px");
        quickName.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);
        quickName.setValueChangeTimeout(350);
        quickName.addClassName("quick-filter");
        quickName.addValueChangeListener(event -> {
            if (!syncingQuickFilters) {
                applyQuickFilters();
            }
        });

        quickClub.setPlaceholder("Club");
        quickClub.getElement().setAttribute("aria-label", "Club");
        quickClub.setClearButtonVisible(true);
        quickClub.setWidth("180px");
        quickClub.addClassName("quick-filter");
        quickClub.addValueChangeListener(event -> {
            if (!syncingQuickFilters) {
                applyQuickFilters();
            }
        });

        quickCaMin.setPlaceholder("CA min");
        quickCaMin.getElement().setAttribute("aria-label", "CA min");
        quickCaMin.setMin(1);
        quickCaMin.setMax(200);
        quickCaMin.setClearButtonVisible(true);
        quickCaMin.setWidth("110px");
        quickCaMin.addClassName("quick-filter");
        quickCaMin.addValueChangeListener(event -> {
            if (!syncingQuickFilters) {
                applyQuickFilters();
            }
        });

        quickAgeMax.setPlaceholder("Age max");
        quickAgeMax.getElement().setAttribute("aria-label", "Age max");
        quickAgeMax.setMin(1);
        quickAgeMax.setMax(80);
        quickAgeMax.setClearButtonVisible(true);
        quickAgeMax.setWidth("110px");
        quickAgeMax.addClassName("quick-filter");
        quickAgeMax.addValueChangeListener(event -> {
            if (!syncingQuickFilters) {
                applyQuickFilters();
            }
        });
    }

    private void configurePlayerShortcuts() {
        Shortcuts.addShortcutListener(playersGrid, () -> navigateSelectedPlayer(-1), Key.ARROW_UP)
                .listenOn(playersGrid);
        Shortcuts.addShortcutListener(playersGrid, () -> navigateSelectedPlayer(1), Key.ARROW_DOWN)
                .listenOn(playersGrid);
        Shortcuts.addShortcutListener(this, this::closePlayerDrawer, Key.ESCAPE)
                .listenOn(this);
    }

    private void navigateSelectedPlayer(int delta) {
        if (tabs.getSelectedTab() != playersTab || visiblePlayers.isEmpty()) {
            return;
        }
        int index = indexOfVisiblePlayer(selectedPlayer);
        int next;
        if (index < 0) {
            next = delta > 0 ? 0 : visiblePlayers.size() - 1;
        } else {
            next = Math.max(0, Math.min(visiblePlayers.size() - 1, index + delta));
        }
        if (index == next && selectedPlayer != null) {
            return;
        }
        openPlayerDrawer(visiblePlayers.get(next));
        playersGrid.scrollToIndex(next);
    }

    private int indexOfVisiblePlayer(PlayerEntity player) {
        if (player == null) {
            return -1;
        }
        for (int i = 0; i < visiblePlayers.size(); i++) {
            if (Objects.equals(visiblePlayers.get(i).getId(), player.getId())) {
                return i;
            }
        }
        return -1;
    }

    private void configureGrid(Grid<?> grid) {
        grid.setSizeFull();
        grid.setColumnReorderingAllowed(true);
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        grid.addClassName("data-grid");
        grid.getElement().getStyle().set("cursor", "default");
    }

    private void refreshSelectedTab() {
        if (tabs.getSelectedTab() == playersTab) {
            showPlayers();
        } else if (tabs.getSelectedTab() == clubsTab) {
            showClubs();
        } else {
            showCompetitions();
        }
    }

    private boolean noDeskClub() {
        String filterClub = playerFilter == null ? "" : playerFilter.club();
        String sessionClub = settings.sessionClub();
        return (filterClub == null || filterClub.isBlank()) && (sessionClub == null || sessionClub.isBlank());
    }

    private void showPlayers() {
        ChatUiContext.setView("Desk");
        ChatUiContext.setFilters(playerFilter.chatSummary());
        List<PlayerColumn> allColumns = allPlayerColumns();
        List<PlayerColumn> columns = showAllPlayerColumns
                ? allColumns
                : allColumns.stream().filter(column -> DEFAULT_PLAYER_COLUMN_KEYS.contains(column.key())).toList();
        String club = playerFilter.club() != null && !playerFilter.club().isBlank()
                ? playerFilter.club()
                : settings.sessionClub();
        List<PlayerEntity> rows;
        if (club == null || club.isBlank()) {
            rows = playerFilter.isEmpty()
                    ? players.findAllPlayerEntities()
                    : players.findPlayerEntities(playerFilter);
        } else if (playerFilter.isEmpty() || playerFilter.isClubOnly()) {
            rows = players.findPlayerEntities(PlayerFilterCriteria.clubOnly(club));
        } else {
            rows = players.findPlayerEntities(playerFilter.withClub(club));
        }
        syncQuickFiltersFromCriteria();
        setPlayerGrid(columns, rows);
        setFilterActive(!playerFilter.isEmpty());
        if (!playerFilter.isEmpty()) {
            renderFilteredStatus("Players", rows.size(), players.countPlayers());
        } else {
            updateStatus(null);
        }
    }

    private List<PlayerColumn> allPlayerColumns() {
        return List.of(
                new PlayerColumn("NAME", "Name", PlayerEntity::getName),
                new PlayerColumn("AGE", "Age", PlayerEntity::getAge),
                new PlayerColumn("HEIGHT_CM", "Height (cm)", PlayerEntity::getHeightCm),
                new PlayerColumn("NATIONALITY", "Nationality", PlayerEntity::getNationality),
                new PlayerColumn("CLUB", "Club", PlayerEntity::getClub),
                new PlayerColumn("PLAYING_CLUB", "Playing Club", PlayerEntity::getPlayingClub),
                new PlayerColumn("POSITION", "Position", PositionTextFormatter::format),
                new PlayerColumn("CA", "CA", PlayerEntity::getCa),
                new PlayerColumn("PA", "PA", PlayerEntity::getPa),
                new PlayerColumn("SALARY_WEEKLY_RAW", "Wage", PlayerEntity::getSalaryWeeklyRaw),
                new PlayerColumn("ASKING_PRICE", "Asking", PlayerEntity::getAskingPrice),
                new PlayerColumn("CONTRACT_END_DATE", "Contract", PlayerEntity::getContractEndDate),
                new PlayerColumn("TRANSFER_LISTED", "Transfer Listed", PlayerEntity::getTransferListed),
                new PlayerColumn("LISTED_FOR_LOAN", "Listed For Loan", PlayerEntity::getListedForLoan),
                new PlayerColumn("TRANSFER_AGREED", "Transfer Agreed", PlayerEntity::getTransferAgreed),
                new PlayerColumn("FUTURE_TRANSFER_CLUB", "Future Transfer Club", PlayerEntity::getFutureTransferClub),
                new PlayerColumn("FUTURE_TRANSFER_DATE", "Future Transfer Date", PlayerEntity::getFutureTransferDate),
                new PlayerColumn("FUTURE_TRANSFER_CONTRACT_END_DATE", "Future Contract End", PlayerEntity::getFutureTransferContractEndDate),
                new PlayerColumn("INJURED", "Injured", PlayerEntity::getInjured),
                new PlayerColumn("INJURY", "Injury", PlayerEntity::getInjury),
                new PlayerColumn("INJURY_LIGHT_TRAINING_DAYS_REMAINING", "Light Training Days", PlayerEntity::getInjuryLightTrainingDaysRemaining),
                new PlayerColumn("INJURY_FULL_TRAINING_DAYS_REMAINING", "Full Training Days", PlayerEntity::getInjuryFullTrainingDaysRemaining),
                new PlayerColumn("INJURY_EXPECTED_RETURN", "Expected Return", PlayerEntity::getInjuryExpectedReturn),
                new PlayerColumn("TRAITS", "Traits", PlayerEntity::getTraits),
                new PlayerColumn("CURRENT_REPUTATION", "Current Reputation", PlayerEntity::getCurrentReputation),
                new PlayerColumn("HOME_REPUTATION", "Home Reputation", PlayerEntity::getHomeReputation),
                new PlayerColumn("WORLD_REPUTATION", "World Reputation", PlayerEntity::getWorldReputation));
    }

    private void showClubs() {
        List<GridColumn> columns = List.of(
                new GridColumn("NAME", "Name"),
                new GridColumn("COMPETITION", "Competition"),
                new GridColumn("NATION", "Nation"),
                new GridColumn("REPUTATION", "Reputation"),
                new GridColumn("BALANCE", "Balance"),
                new GridColumn("TRANSFER_BUDGET", "Transfer Budget"),
                new GridColumn("PAYROLL_BUDGET", "Payroll Budget"));
        List<ClubEntity> rows = clubs.findClubEntities(clubFilter);
        setClubGrid(columns, rows);
        setFilterActive(!clubFilter.isEmpty());
        if (!clubFilter.isEmpty()) {
            renderFilteredStatus("Clubs", rows.size(), clubs.countClubs());
        } else {
            updateStatus(null);
        }
    }

    private void showCompetitions() {
        List<GridColumn> columns = List.of(
                new GridColumn("NAME", "Name"),
                new GridColumn("NATION", "Nation"),
                new GridColumn("REPUTATION", "Reputation"),
                new GridColumn("GENDER", "Gender"));
        List<CompetitionEntity> rows = competitions.findCompetitionEntities(competitionFilter);
        setCompetitionGrid(columns, rows);
        setFilterActive(!competitionFilter.isEmpty());
        if (!competitionFilter.isEmpty()) {
            renderFilteredStatus("Competitions", rows.size(), competitions.countCompetitions());
        } else {
            updateStatus(null);
        }
    }

    private void setClubGrid(List<GridColumn> columns, List<ClubEntity> rows) {
        if (!clubsColumnsBuilt) {
            clubsGrid.removeAllColumns();
            for (GridColumn column : columns) {
                clubsGrid.addColumn(club -> displayColumn(column.key(), clubColumnValue(club, column.key())))
                        .setKey(column.key())
                        .setHeader(column.header())
                        .setAutoWidth(true)
                        .setResizable(true)
                        .setComparator((left, right) -> compareClubColumn(left, right, column.key()))
                        .setSortable(true);
            }
            clubsColumnsBuilt = true;
        }
        clubsGrid.setItems(rows);
        showWorkspace(
                "Clubs",
                rows.size(),
                clubsGrid,
                null,
                false,
                "No clubs match",
                "Clear filters or load RAM data to populate clubs.");
    }

    private void setCompetitionGrid(List<GridColumn> columns, List<CompetitionEntity> rows) {
        if (!competitionsColumnsBuilt) {
            competitionsGrid.removeAllColumns();
            for (GridColumn column : columns) {
                competitionsGrid.addColumn(competition -> displayColumn(column.key(), competitionColumnValue(competition, column.key())))
                        .setKey(column.key())
                        .setHeader(column.header())
                        .setAutoWidth(true)
                        .setResizable(true)
                        .setComparator((left, right) -> compareCompetitionColumn(left, right, column.key()))
                        .setSortable(true);
            }
            competitionsColumnsBuilt = true;
        }
        competitionsGrid.setItems(rows);
        showWorkspace(
                "Competitions",
                rows.size(),
                competitionsGrid,
                null,
                false,
                "No competitions match",
                "Clear filters or load RAM data to populate competitions.");
    }

    private void setPlayerGrid(List<PlayerColumn> columns, List<PlayerEntity> rows) {
        boolean mode = showAllPlayerColumns;
        if (!playersColumnsBuilt || playersColumnsAllMode != mode) {
            playersGrid.removeAllColumns();
            playersGrid.setPartNameGenerator(this::playerRowPartName);
            for (PlayerColumn column : columns) {
                Grid.Column<PlayerEntity> gridColumn;
                if ("NAME".equals(column.key())) {
                    gridColumn = playersGrid.addColumn(new ComponentRenderer<>(this::playerNameCell))
                            .setFlexGrow(1)
                            .setWidth("220px");
                } else if ("CA".equals(column.key()) || "PA".equals(column.key())) {
                    gridColumn = playersGrid.addColumn(new ComponentRenderer<>(player -> abilityCell(column.value(player))))
                            .setAutoWidth(true);
                } else {
                    gridColumn = playersGrid.addColumn(player -> displayColumn(column.key(), column.value(player)))
                            .setAutoWidth(true);
                }
                gridColumn
                        .setKey(column.key())
                        .setHeader(column.header())
                        .setResizable(true)
                        .setComparator((left, right) -> comparePlayerColumn(left, right, column))
                        .setSortable(true);
                if ("NAME".equals(column.key())) {
                    gridColumn.setFrozen(true);
                }
            }
            playersColumnsBuilt = true;
            playersColumnsAllMode = mode;
        }
        playersGrid.setItems(rows);
        visiblePlayers = List.copyOf(rows);
        if (selectedPlayer != null) {
            PlayerEntity stillVisible = rows.stream()
                    .filter(player -> Objects.equals(player.getId(), selectedPlayer.getId()))
                    .findFirst()
                    .orElse(null);
            selectedPlayer = stillVisible;
            if (stillVisible != null) {
                playersGrid.select(stillVisible);
                int index = indexOfVisiblePlayer(stillVisible);
                if (index >= 0) {
                    playersGrid.scrollToIndex(index);
                }
            } else {
                playersGrid.deselectAll();
            }
        }
        if (compareAnchor != null) {
            compareAnchor = rows.stream()
                    .filter(player -> Objects.equals(player.getId(), compareAnchor.getId()))
                    .findFirst()
                    .orElse(null);
            if (compareAnchor == null) {
                awaitingCompareSelection = false;
            }
        }
        showWorkspace(
                "Players",
                rows.size(),
                playersGrid,
                buildSidePanel(),
                true,
                "No players match",
                noDeskClub()
                        ? "No players yet — load from RAM to fill the desk, or pick your club in the top bar to focus on your squad."
                        : "Adjust filters or load from RAM to fill the desk.");
    }

    private void showWorkspace(
            String title,
            int rowCount,
            Component grid,
            Component drawer,
            boolean playersMode,
            String emptyTitle,
            String emptyBody) {
        content.removeAll();
        content.setSizeFull();
        content.addClassName("data-workspace");
        content.getElement().getClassList().set("has-drawer", drawer != null);

        Span titleText = new Span(title);
        titleText.addClassName("workspace-title");
        Span countText = new Span(rowCount + (rowCount == 1 ? " row" : " rows"));
        countText.addClassName("workspace-count");
        Div titleBlock = new Div(titleText, countText);
        titleBlock.addClassName("workspace-title-block");

        Div toolbarRight = new Div();
        toolbarRight.addClassName("workspace-toolbar-right");
        if (playersMode) {
            Span hintText = new Span("Select a row · ↑ ↓ to move");
            hintText.addClassName("workspace-hint");
            toolbarRight.add(hintText);
        }
        Div toolbar = new Div(titleBlock, toolbarRight);
        toolbar.addClassName("workspace-toolbar");
        content.add(toolbar);

        if (playersMode) {
            content.add(playerQuickFilterBar());
            Div chips = playerFilterChips();
            if (chips.getChildren().findAny().isPresent()) {
                content.add(chips);
            }
            if (!playerFilter.isEmpty() && playerFilter.club() != null && !playerFilter.club().isBlank()) {
                content.add(loanLegend());
            }
        }

        if (rowCount == 0) {
            content.add(emptyState(emptyTitle, emptyBody));
            return;
        }

        Div stage = new Div(grid);
        stage.addClassName("workspace-stage");
        stage.setSizeFull();

        if (drawer == null) {
            content.add(stage);
            return;
        }

        Div body = new Div(stage, drawer);
        body.addClassName("workspace-body");
        body.setSizeFull();
        content.add(body);
    }

    private Div playerQuickFilterBar() {
        Span label = new Span("Quick");
        label.addClassName("quick-filter-label");
        Div bar = new Div(label, quickName, quickClub, quickCaMin, quickAgeMax);
        bar.addClassName("quick-filter-bar");
        return bar;
    }

    private Div playerFilterChips() {
        Div chips = new Div();
        chips.addClassName("filter-chips");
        addFilterChip(chips, "Name", meaningfulText(playerFilter.name()), () -> patchPlayerFilter("", null, null, null));
        addFilterChip(chips, "Club", meaningfulText(playerFilter.club()), () -> patchPlayerFilter(null, "", null, null));
        addFilterChip(chips, "Nation", meaningfulText(playerFilter.playingNation()), () -> clearPlayingNation());
        addFilterChip(chips, "Competition", meaningfulText(playerFilter.playingCompetition()), () -> clearPlayingCompetition());
        addFilterChip(chips, "Nationality", meaningfulText(playerFilter.nationality()), () -> clearNationality());
        if (playerFilter.ageMin() != null || playerFilter.ageMax() != null) {
            String age = rangeLabel(playerFilter.ageMin(), playerFilter.ageMax());
            addFilterChip(chips, "Age", age, () -> patchPlayerFilter(null, null, null, true));
        }
        if (meaningfulMin(playerFilter.caMin()) || playerFilter.caMax() != null) {
            addFilterChip(chips, "CA", rangeLabel(meaningfulMinValue(playerFilter.caMin()), playerFilter.caMax()), this::clearCaFilter);
        }
        if (meaningfulMin(playerFilter.paMin()) || playerFilter.paMax() != null) {
            addFilterChip(chips, "PA", rangeLabel(meaningfulMinValue(playerFilter.paMin()), playerFilter.paMax()), this::clearPaFilter);
        }
        if (playerFilter.salaryMax() != null) {
            addFilterChip(chips, "Wage ≤", moneyDisplay(playerFilter.salaryMax()), this::clearSalaryMax);
        }
        if (!playerFilter.positionMinimums().isEmpty()) {
            addFilterChip(chips, "Positions", playerFilter.positionMinimums().size() + " set", this::clearPositions);
        }
        if (!playerFilter.attributeMinimums().isEmpty()) {
            addFilterChip(chips, "Attributes", playerFilter.attributeMinimums().size() + " set", this::clearAttributes);
        }
        return chips;
    }

    private void addFilterChip(Div chips, String label, String value, Runnable clearAction) {
        if (value == null || value.isBlank()) {
            return;
        }
        Span text = new Span(label + ": " + value);
        text.addClassName("filter-chip-text");
        text.getElement().setAttribute("title", "Edit filters");
        text.addClickListener(event -> openFilterDialog());
        Button clear = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> clearAction.run());
        clear.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        clear.addClassName("filter-chip-clear");
        clear.getElement().setAttribute("aria-label", "Clear " + label);
        Div chip = new Div(text, clear);
        chip.addClassName("filter-chip");
        chips.add(chip);
    }

    private static Div loanLegend() {
        Span out = new Span("Loaned out");
        out.addClassName("legend-swatch");
        out.addClassName("legend-loaned-out");
        Span in = new Span("Loaned in");
        in.addClassName("legend-swatch");
        in.addClassName("legend-loaned-in");
        Div legend = new Div(out, in);
        legend.addClassName("loan-legend");
        return legend;
    }

    private static Div emptyState(String title, String body) {
        Span titleText = new Span(title);
        titleText.addClassName("empty-state-title");
        Span bodyText = new Span(body);
        bodyText.addClassName("empty-state-copy");
        Div icon = new Div(VaadinIcon.SEARCH.create());
        icon.addClassName("empty-state-icon");
        Div state = new Div(icon, titleText, bodyText);
        state.addClassName("empty-state");
        return state;
    }

    private Component playerNameCell(PlayerEntity player) {
        Span name = new Span(display(player.getName()));
        name.addClassName("player-name");
        Div badges = new Div();
        badges.addClassName("player-badges");
        if (Boolean.TRUE.equals(player.getInjured())) {
            Span injured = new Span("INJ");
            injured.addClassName("row-badge");
            injured.addClassName("row-badge-injury");
            badges.add(injured);
        }
        if (Boolean.TRUE.equals(player.getTransferListed())) {
            Span listed = new Span("Listed");
            listed.addClassName("row-badge");
            listed.addClassName("row-badge-transfer");
            badges.add(listed);
        }
        Div cell = new Div(name, badges);
        cell.addClassName("player-name-cell");
        return cell;
    }

    private Component abilityCell(Object value) {
        Span text = new Span(display(value));
        text.addClassName("ability-value");
        Div cell = new Div(text);
        cell.addClassName("ability-cell");
        String tone = abilityTone(value);
        if (tone != null) {
            cell.addClassName(tone);
        }
        return cell;
    }

    private static String abilityTone(Object value) {
        Long score = sortableLong(value);
        if (score == null) {
            return null;
        }
        if (score >= 160) {
            return "ability-elite";
        }
        if (score >= 140) {
            return "ability-high";
        }
        if (score >= 120) {
            return "ability-mid";
        }
        return "ability-low";
    }

    private void refreshSavedViewOptions() {
        syncingSavedViews = true;
        try {
            List<String> names = settings.playerViews().stream().map(SavedPlayerView::name).toList();
            String current = savedViews.getValue();
            savedViews.setItems(names);
            if (current != null && names.stream().anyMatch(name -> name.equalsIgnoreCase(current))) {
                savedViews.setValue(names.stream()
                        .filter(name -> name.equalsIgnoreCase(current))
                        .findFirst()
                        .orElse(null));
            } else {
                savedViews.clear();
            }
        } finally {
            syncingSavedViews = false;
        }
    }

    private void applySavedView(String name) {
        settings.playerViews().stream()
                .filter(view -> view.name().equalsIgnoreCase(name))
                .findFirst()
                .ifPresent(view -> {
                    playerFilter = view.filter() == null ? PlayerFilterCriteria.empty() : view.filter();
                    showAllPlayerColumns = view.showAllColumns();
                    columnsButton.setText(showAllPlayerColumns ? "Key columns" : "All columns");
                    selectedPlayer = null;
                    clearCompareState();
                    showPlayers();
                });
    }

    private void openSaveViewDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Save player view");
        dialog.setWidth("420px");
        dialog.setMaxWidth("calc(100vw - 32px)");
        dialog.getElement().getThemeList().add("professional-dialog");

        TextField name = new TextField("View name");
        name.setWidthFull();
        name.setValue(savedViews.getValue() == null ? "" : savedViews.getValue());
        name.setClearButtonVisible(true);
        Span help = new Span("Stores the current player filters and column mode.");
        help.addClassName("settings-intro");
        VerticalLayout layout = new VerticalLayout(help, name);
        layout.setPadding(false);
        layout.setSpacing(true);
        dialog.add(layout);

        Button save = new Button("Save", VaadinIcon.CHECK.create(), event -> {
            String viewName = meaningfulText(name.getValue());
            if (viewName == null) {
                Notification.show("Enter a view name", 3000, Notification.Position.TOP_CENTER);
                return;
            }
            settings.savePlayerView(new SavedPlayerView(viewName, playerFilter, showAllPlayerColumns));
            refreshSavedViewOptions();
            syncingSavedViews = true;
            try {
                savedViews.setValue(viewName);
            } finally {
                syncingSavedViews = false;
            }
            Notification saved = Notification.show("View saved", 2500, Notification.Position.TOP_CENTER);
            saved.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            saved.addClassName("app-toast");
            dialog.close();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", VaadinIcon.CLOSE_SMALL.create(), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
        name.focus();
    }

    private void deleteSelectedView() {
        String name = savedViews.getValue();
        if (name == null || name.isBlank()) {
            Notification.show("Select a saved view to delete", 2500, Notification.Position.TOP_CENTER);
            return;
        }
        settings.deletePlayerView(name);
        refreshSavedViewOptions();
        Notification deleted = Notification.show("View deleted", 2500, Notification.Position.TOP_CENTER);
        deleted.addClassName("app-toast");
    }

    private String playerRowPartName(PlayerEntity player) {
        String filterClub = playerFilter.club();
        if (filterClub == null || filterClub.isBlank()) {
            return null;
        }
        boolean contractedToFilter = sameText(player.getClub(), filterClub);
        boolean playingAtFilter = sameText(player.getPlayingClub(), filterClub);
        if (contractedToFilter && !playingAtFilter) {
            return "contract-club-loaned-out";
        }
        if (playingAtFilter && !contractedToFilter) {
            return "playing-club-loaned-in";
        }
        return null;
    }

    private void openPlayerDrawer(PlayerEntity player) {
        if (awaitingCompareSelection && compareAnchor != null
                && !Objects.equals(compareAnchor.getId(), player.getId())) {
            selectedPlayer = player;
            awaitingCompareSelection = false;
            refreshPlayerDrawer();
            return;
        }
        selectedPlayer = player;
        refreshPlayerDrawer();
    }

    private void closePlayerDrawer() {
        if (selectedPlayer == null && compareAnchor == null && !awaitingCompareSelection) {
            return;
        }
        selectedPlayer = null;
        clearCompareState();
        refreshPlayerDrawer();
    }

    private void refreshPlayerDrawer() {
        if (visiblePlayers.isEmpty() && selectedPlayer == null) {
            showPlayers();
            return;
        }
        if (selectedPlayer != null) {
            playersGrid.select(selectedPlayer);
        } else {
            playersGrid.deselectAll();
        }
        showWorkspace(
                "Players",
                visiblePlayers.size(),
                playersGrid,
                buildSidePanel(),
                true,
                "No players match",
                "Adjust filters or load from RAM to fill the desk.");
    }

    private void clearCompareState() {
        compareAnchor = null;
        awaitingCompareSelection = false;
    }

    private void startCompare() {
        if (selectedPlayer == null) {
            return;
        }
        compareAnchor = selectedPlayer;
        awaitingCompareSelection = true;
        Notification notice = Notification.show(
                "Select another player to compare",
                2500,
                Notification.Position.TOP_CENTER);
        notice.addClassName("app-toast");
        refreshPlayerDrawer();
    }

    private Component buildSidePanel() {
        if (compareAnchor != null && selectedPlayer != null
                && !Objects.equals(compareAnchor.getId(), selectedPlayer.getId())) {
            return buildCompareDrawer(compareAnchor, selectedPlayer);
        }
        return buildPlayerDrawer();
    }

    private Component buildCompareDrawer(PlayerEntity left, PlayerEntity right) {
        Span title = new Span("Compare");
        title.addClassName("drawer-title");
        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> closePlayerDrawer());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        close.addClassName("drawer-close");
        close.getElement().setAttribute("aria-label", "Close compare");
        Div header = new Div(title, close);
        header.addClassName("drawer-header");

        Div grid = new Div(
                compareMetric("Name", display(left.getName()), display(right.getName()), null),
                compareMetric("Age", display(left.getAge()), display(right.getAge()),
                        compareLongs(sortableLong(left.getAge()), sortableLong(right.getAge()), false)),
                compareMetric("Club", display(left.getClub()), display(right.getClub()), null),
                compareMetric("Position", PositionTextFormatter.format(left), PositionTextFormatter.format(right), null),
                compareMetric("Traits", display(left.getTraits()), display(right.getTraits()), null),
                compareMetric("CA", display(left.getCa()), display(right.getCa()),
                        compareLongs(sortableLong(left.getCa()), sortableLong(right.getCa()), true)),
                compareMetric("PA", display(left.getPa()), display(right.getPa()),
                        compareLongs(sortableLong(left.getPa()), sortableLong(right.getPa()), true)),
                compareMetric("Wage", salaryWeeklyDisplay(left.getSalaryWeeklyRaw()), salaryWeeklyDisplay(right.getSalaryWeeklyRaw()),
                        compareLongs(sortableLong(left.getSalaryWeeklyRaw()), sortableLong(right.getSalaryWeeklyRaw()), false)),
                compareMetric("Asking", moneyDisplay(left.getAskingPrice()), moneyDisplay(right.getAskingPrice()),
                        compareLongs(sortableLong(left.getAskingPrice()), sortableLong(right.getAskingPrice()), false)),
                compareMetric("Contract", display(left.getContractEndDate()), display(right.getContractEndDate()), null));
        grid.addClassName("compare-grid");

        Div body = new Div(grid);
        body.addClassName("drawer-content");
        Div drawer = new Div(header, body);
        drawer.addClassName("player-drawer");
        drawer.addClassName("compare-drawer");
        return drawer;
    }

    private static Integer compareLongs(Long left, Long right, boolean higherIsBetter) {
        if (left == null || right == null || left.equals(right)) {
            return null;
        }
        boolean leftWins = higherIsBetter ? left > right : left < right;
        return leftWins ? -1 : 1;
    }

    private static Div compareMetric(String label, String left, String right, Integer winner) {
        Span labelText = new Span(label);
        labelText.addClassName("compare-name");
        Span leftText = new Span(blankDash(left));
        leftText.addClassName("compare-value");
        leftText.addClassName("compare-left");
        Span rightText = new Span(blankDash(right));
        rightText.addClassName("compare-value");
        rightText.addClassName("compare-right");
        if (winner != null) {
            if (winner < 0) {
                leftText.addClassName("compare-winner");
            } else {
                rightText.addClassName("compare-winner");
            }
        }
        Div row = new Div(labelText, leftText, rightText);
        row.addClassName("compare-row");
        return row;
    }

    private static String blankDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private Component buildPlayerDrawer() {
        if (selectedPlayer == null) {
            return null;
        }
        PlayerEntity player = selectedPlayer;

        Span title = new Span(display(player.getName()));
        title.addClassName("drawer-title");
        Button previous = new Button(VaadinIcon.ARROW_UP.create(), event -> navigateSelectedPlayer(-1));
        previous.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        previous.addClassName("drawer-nav");
        previous.setTooltipText("Previous player (↑)");
        previous.getElement().setAttribute("aria-label", "Previous player");
        Button next = new Button(VaadinIcon.ARROW_DOWN.create(), event -> navigateSelectedPlayer(1));
        next.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        next.addClassName("drawer-nav");
        next.setTooltipText("Next player (↓)");
        next.getElement().setAttribute("aria-label", "Next player");
        Button compare = new Button("Compare", event -> startCompare());
        compare.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        compare.addClassName("drawer-compare");
        compare.setTooltipText(awaitingCompareSelection
                ? "Waiting for second player"
                : "Compare with another player");
        compare.getElement().setAttribute("aria-label", "Compare with another player");
        Button argue = new Button("Ask FM AI", event ->
                ChatLaunch.open(ChatLaunch.askAbout(player.getName(), settings.sessionClub())));
        argue.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> closePlayerDrawer());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        close.addClassName("drawer-close");
        close.getElement().setAttribute("aria-label", "Close player details");
        Div nav = new Div(argue, compare, previous, next, close);
        nav.addClassName("drawer-actions");
        Div header = new Div(title, nav);
        header.addClassName("drawer-header");
        Span navHint = new Span(awaitingCompareSelection
                ? "Select another player to compare · Esc to cancel"
                : "↑ ↓ to move · Esc to close");
        navHint.addClassName("drawer-nav-hint");

        Div summary = playerSummary(player);
        summary.addClassName("drawer-summary");
        Span ramLimit = new Span("Morale, form and match stats are not read from RAM yet.");
        ramLimit.addClassName("drawer-nav-hint");

        VerticalLayout info = new VerticalLayout(
                detailSection("Profile", List.of(
                        new DetailField("Age", player.getAge()),
                        new DetailField("Height", heightDisplay(player)),
                        new DetailField("Nationality", player.getNationality()),
                        new DetailField("Position", PositionTextFormatter.format(player)),
                        new DetailField("Club", player.getClub()),
                        new DetailField("Playing Club", player.getPlayingClub()),
                        new DetailField("Traits", player.getTraits()))),
                detailSection("Contract", List.of(
                        new DetailField("Salary Weekly", salaryWeeklyDisplay(player.getSalaryWeeklyRaw())),
                        new DetailField("Asking Price", moneyDisplay(player.getAskingPrice())),
                        new DetailField("Joined Club Date", player.getJoinedClubDate()),
                        new DetailField("Contract End Date", player.getContractEndDate()))),
                detailSection("Injury", List.of(
                        new DetailField("Injured", player.getInjured() == null
                                ? "Unknown"
                                : Boolean.TRUE.equals(player.getInjured()) ? "Yes" : "No"),
                        new DetailField("Injury", player.getInjury()),
                        new DetailField("Expected return", player.getInjuryExpectedReturn()),
                        new DetailField("Days remaining", player.getInjuryMinDaysRemaining()))),
                detailSection("Reputation", List.of(
                        new DetailField("Current Reputation", player.getCurrentReputation()),
                        new DetailField("Home Reputation", player.getHomeReputation()),
                        new DetailField("World Reputation", player.getWorldReputation()))));
        info.setPadding(false);
        info.setSpacing(true);
        info.addClassName("detail-info");

        Checkbox showGoalkeeping = new Checkbox("Show goalkeeping attributes");
        showGoalkeeping.setValue(isGoalkeeper(player));
        ComboBox<String> inPossessionRole = roleComboBox("In possession role", PlayerRoleAttributeCatalog.IN_POSSESSION);
        ComboBox<String> outOfPossessionRole = roleComboBox("Out of possession role", PlayerRoleAttributeCatalog.OUT_OF_POSSESSION);
        inPossessionRole.setWidthFull();
        outOfPossessionRole.setWidthFull();
        Div attributes = new Div();
        attributes.setWidthFull();
        attributes.addClassName("attribute-panel");
        renderAttributeColumns(attributes, player, showGoalkeeping.getValue(), Map.of());
        showGoalkeeping.addValueChangeListener(event -> renderAttributeColumns(
                attributes,
                player,
                event.getValue(),
                selectedRolePriorities(inPossessionRole, outOfPossessionRole)));
        inPossessionRole.addValueChangeListener(event -> {
            if (event.getValue() != null && !event.getValue().isBlank()) {
                outOfPossessionRole.clear();
            }
            renderAttributeColumns(attributes, player, showGoalkeeping.getValue(), selectedRolePriorities(inPossessionRole, outOfPossessionRole));
        });
        outOfPossessionRole.addValueChangeListener(event -> {
            if (event.getValue() != null && !event.getValue().isBlank()) {
                inPossessionRole.clear();
            }
            renderAttributeColumns(attributes, player, showGoalkeeping.getValue(), selectedRolePriorities(inPossessionRole, outOfPossessionRole));
        });
        VerticalLayout attributeToolbar = new VerticalLayout(showGoalkeeping, inPossessionRole, outOfPossessionRole);
        attributeToolbar.setPadding(false);
        attributeToolbar.setSpacing(true);
        attributeToolbar.addClassName("attribute-toolbar");
        VerticalLayout attributesView = new VerticalLayout(attributeToolbar, attributes);
        attributesView.setPadding(false);
        attributesView.setSpacing(true);
        attributesView.addClassName("attributes-view");

        Div positions = positionsPanel(player);

        Tab infoTab = new Tab("Info");
        Tab attributesTab = new Tab("Attributes");
        Tab positionsTab = new Tab("Positions");
        Tabs detailTabs = new Tabs(infoTab, attributesTab, positionsTab);
        detailTabs.addClassName("dialog-tabs");
        detailTabs.addClassName("drawer-tabs");
        Div detailContent = new Div(info);
        detailContent.setWidthFull();
        detailContent.addClassName("drawer-content");
        detailTabs.addSelectedChangeListener(event -> {
            detailContent.removeAll();
            if (event.getSelectedTab() == infoTab) {
                detailContent.add(info);
            } else if (event.getSelectedTab() == attributesTab) {
                detailContent.add(attributesView);
            } else {
                detailContent.add(positions);
            }
        });

        Div drawer = new Div(header, navHint, summary, ramLimit, detailTabs, detailContent);
        drawer.addClassName("player-drawer");
        return drawer;
    }

    private Div playerSummary(PlayerEntity player) {
        Div summary = new Div(
                summaryMetric("CA", display(player.getCa())),
                summaryMetric("PA", display(player.getPa())),
                summaryMetric("Age", display(player.getAge())),
                summaryMetric("Position", PositionTextFormatter.format(player)),
                summaryMetric("Wage", salaryWeeklyDisplay(player.getSalaryWeeklyRaw())),
                summaryMetric("Asking", moneyDisplay(player.getAskingPrice())));
        summary.addClassName("player-summary");
        return summary;
    }

    private static Div summaryMetric(String label, String value) {
        Span labelText = new Span(label);
        labelText.addClassName("summary-label");
        Span valueText = new Span(value == null || value.isBlank() ? "—" : value);
        valueText.addClassName("summary-value");
        Div metric = new Div(labelText, valueText);
        metric.addClassName("summary-metric");
        return metric;
    }

    private static Div detailSection(String title, List<DetailField> fields) {
        Span heading = new Span(title);
        heading.addClassName("detail-section-title");
        Div section = new Div(heading, detailLayout(fields));
        section.addClassName("detail-section");
        return section;
    }

    private static Div positionsPanel(PlayerEntity player) {
        Span heading = new Span("Positional familiarity");
        heading.addClassName("positions-heading");
        Span legend = new Span("Natural · Accomplished · Competent · Can play · Cannot");
        legend.addClassName("positions-legend");
        Div panel = new Div(heading, legend, positionField(player));
        panel.addClassName("positions-panel");
        return panel;
    }

    private void openFilterDialog() {
        if (tabs.getSelectedTab() == clubsTab) {
            openClubFilterDialog();
        } else if (tabs.getSelectedTab() == competitionsTab) {
            openCompetitionFilterDialog();
        } else {
            openPlayerFilterDialog();
        }
    }

    private void openPlayerFilterDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Player filter");
        dialog.setWidth("1280px");
        dialog.setMaxWidth("calc(100vw - 32px)");
        dialog.setMaxHeight("calc(100vh - 32px)");
        dialog.getElement().getThemeList().add("professional-dialog");
        dialog.getElement().getThemeList().add("filter-dialog");
        dialog.getElement().getThemeList().add("wide-dialog");

        TextField name = new TextField("Name contains");
        name.setValue(nullSafeValue(playerFilter.name()));
        Select<String> gender = new Select<>();
        gender.setLabel("Gender");
        gender.setItems("", "male", "female");
        gender.setItemLabelGenerator(value -> value == null || value.isBlank() ? "Any" : value);
        gender.setValue(nullSafeValue(playerFilter.gender()));
        ComboBox<String> playingNation = comboBox("Playing nation", players.findPlayingNations(), playerFilter.playingNation());
        ComboBox<String> playingCompetition = comboBox("Playing competition", players.findPlayingCompetitions(), playerFilter.playingCompetition());
        ComboBox<String> club = comboBox("Club", players.findClubs(), playerFilter.club());
        ComboBox<String> nationality = comboBox("Nationality", players.findNationalities(), playerFilter.nationality());
        nationality.setValue(nullSafeValue(playerFilter.nationality()));

        IntegerField ageMin = intField("Age min", playerFilter.ageMin(), 1, 80);
        IntegerField ageMax = intField("Age max", playerFilter.ageMax(), 1, 80);
        IntegerField heightMin = intField("Height min (cm)", playerFilter.heightMin(), 100, 230);
        IntegerField heightMax = intField("Height max (cm)", playerFilter.heightMax(), 100, 230);
        IntegerField currentRepMin = intField("Current rep min", playerFilter.currentReputationMin(), 1, 10000);
        IntegerField currentRepMax = intField("Current rep max", playerFilter.currentReputationMax(), 1, 10000);
        IntegerField homeRepMin = intField("Home rep min", playerFilter.homeReputationMin(), 1, 10000);
        IntegerField homeRepMax = intField("Home rep max", playerFilter.homeReputationMax(), 1, 10000);
        IntegerField worldRepMin = intField("World rep min", playerFilter.worldReputationMin(), 1, 10000);
        IntegerField worldRepMax = intField("World rep max", playerFilter.worldReputationMax(), 1, 10000);
        IntegerField caMin = intField("CA min", playerFilter.caMin(), 1, 200);
        IntegerField caMax = intField("CA max", playerFilter.caMax(), 1, 200);
        IntegerField paMin = intField("PA min", playerFilter.paMin(), 1, 200);
        IntegerField paMax = intField("PA max", playerFilter.paMax(), 1, 200);
        LongField askingMin = new LongField("Asking price min", fromDisplayPounds(playerFilter.askingPriceMin()));
        LongField askingMax = new LongField("Asking price max", fromDisplayPounds(playerFilter.askingPriceMax()));
        LongField salaryMax = new LongField("Weekly Salary max", fromDisplayPounds(playerFilter.salaryMax()));
        DatePicker contractFrom = new DatePicker("Contract end from");
        contractFrom.setValue(playerFilter.contractEndDateFrom());
        DatePicker contractTo = new DatePicker("Contract end to");
        contractTo.setValue(playerFilter.contractEndDateTo());

        FormLayout basicFilters = new FormLayout(
                name, gender,
                playingNation, playingCompetition,
                ageMin, ageMax,
                heightMin, heightMax,
                club, salaryMax.field(),
                askingMin.field(), askingMax.field(),
                contractFrom, contractTo,
                caMin, caMax,
                paMin, paMax,
                currentRepMin, currentRepMax,
                homeRepMin, homeRepMax,
                worldRepMin, worldRepMax,
                nationality
                );
        basicFilters.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("720px", 2));
        basicFilters.addClassName("filter-form");

        Span filterIntro = new Span("Filter by identity, ability, contract, or reputation.");
        filterIntro.addClassName("filter-intro");
        VerticalLayout playerTab = new VerticalLayout(filterIntro, basicFilters);
        playerTab.setPadding(false);
        playerTab.setSpacing(true);
        playerTab.addClassName("filter-tab-content");

        Map<String, PositionLevel> selectedPositions = new LinkedHashMap<>();
        for (FieldDef field : AttributeDefinitions.POSITION_FIELDS) {
            String column = PlayerColumnNames.toColumnName(field.name()).toUpperCase();
            PositionLevel initial = PositionLevel.fromMinimum(playerFilter.positionMinimums().get(column));
            selectedPositions.put(column, initial);
        }

        Div positionFilterLayout = positionFilterField(selectedPositions);

        Map<String, IntegerField> attributeFields = new LinkedHashMap<>();
        Div attributeLayout = new Div();
        attributeLayout.setWidthFull();
        attributeLayout.addClassName("attribute-filter-grid");
        attributeLayout.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fill, minmax(160px, 1fr))")
                .set("gap", "16px")
                .set("align-items", "start");

        Tab filtersTab = new Tab("Filters");
        Tab positionsTab = new Tab("Positions");
        Tab attributesTab = new Tab("Attributes");
        Tabs dialogTabs = new Tabs(filtersTab, positionsTab, attributesTab);
        dialogTabs.addClassName("dialog-tabs");
        Div dialogContent = new Div(playerTab);
        dialogContent.setWidthFull();
        dialogContent.addClassName("dialog-content");
        dialogContent.addClassName("filter-dialog-content");
        dialogTabs.addSelectedChangeListener(event -> {
            dialogContent.removeAll();
            if (event.getSelectedTab() == filtersTab) {
                dialogContent.add(playerTab);
            } else if (event.getSelectedTab() == positionsTab) {
                dialogContent.add(positionFilterLayout);
            } else {
                createAttributeFields(attributeFields, attributeLayout);
                dialogContent.add(attributeLayout);
            }
        });

        Button apply = new Button("Apply filters", VaadinIcon.CHECK.create(), event -> {
            createAttributeFields(attributeFields, attributeLayout);
            if (!validPlayerFilter(
                    currentRepMin, currentRepMax,
                    homeRepMin, homeRepMax,
                    worldRepMin, worldRepMax,
                    caMin, caMax,
                    paMin, paMax,
                    heightMin, heightMax,
                    attributeFields)) {
                return;
            }
            playerFilter = new PlayerFilterCriteria(
                    name.getValue(),
                    gender.getValue(),
                    playingNation.getValue(),
                    playingCompetition.getValue(),
                    club.getValue(),
                    ageMin.getValue(), ageMax.getValue(),
                    heightMin.getValue(), heightMax.getValue(),
                    nationality.getValue(),
                    currentRepMin.getValue(), currentRepMax.getValue(),
                    homeRepMin.getValue(), homeRepMax.getValue(),
                    worldRepMin.getValue(), worldRepMax.getValue(),
                    caMin.getValue(), caMax.getValue(),
                    paMin.getValue(), paMax.getValue(),
                    contractFrom.getValue(), contractTo.getValue(),
                    toFilterPounds(askingMin.value()), toFilterPounds(askingMax.value()),
                    toFilterPounds(salaryMax.value()),
                    selectedPositionMinimums(selectedPositions),
                    selectedAttributeMinimums(attributeFields));
            showPlayers();
            dialog.close();
        });
        apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button clear = new Button("Clear", VaadinIcon.TRASH.create(), event -> {
            playerFilter = PlayerFilterCriteria.empty();
            showPlayers();
            updateStatus(null);
            dialog.close();
        });
        clear.addThemeVariants(ButtonVariant.LUMO_ERROR);
        Button cancel = new Button("Cancel", VaadinIcon.CLOSE_SMALL.create(), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        dialog.add(dialogTabs, dialogContent);
        dialog.getFooter().add(clear, cancel, apply);
        dialog.open();
    }

    private void openClubFilterDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Club filter");
        dialog.setWidth("1280px");
        dialog.setMaxWidth("calc(100vw - 32px)");
        dialog.setMaxHeight("calc(100vh - 32px)");
        dialog.getElement().getThemeList().add("professional-dialog");
        dialog.getElement().getThemeList().add("filter-dialog");

        ComboBox<String> name = comboBox("Name", clubs.findNames(), clubFilter.name());
        ComboBox<String> competition = comboBox("Competition", clubs.findCompetitionNames(), clubFilter.competition());
        ComboBox<String> nation = comboBox("Nation", clubs.findNations(), clubFilter.nation());

        IntegerField reputationMin = intField("Reputation min", clubFilter.reputationMin(), 1, 10000);
        IntegerField reputationMax = intField("Reputation max", clubFilter.reputationMax(), 1, 10000);
        LongField balanceMin = new LongField("Balance min", clubFilter.balanceMin());
        LongField balanceMax = new LongField("Balance max", clubFilter.balanceMax());
        LongField transferMin = new LongField("Transfer budget min", clubFilter.transferBudgetMin());
        LongField transferMax = new LongField("Transfer budget max", clubFilter.transferBudgetMax());
        LongField payrollMin = new LongField("Payroll budget min", clubFilter.payrollBudgetMin());
        LongField payrollMax = new LongField("Payroll budget max", clubFilter.payrollBudgetMax());

        FormLayout filters = new FormLayout(
                name, competition,
                nation,
                reputationMin, reputationMax,
                balanceMin.field(), balanceMax.field(),
                transferMin.field(), transferMax.field(),
                payrollMin.field(), payrollMax.field());
        filters.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("720px", 2));
        filters.addClassName("filter-form");

        Div dialogContent = new Div(filters);
        dialogContent.setWidthFull();
        dialogContent.addClassName("dialog-content");
        dialogContent.addClassName("filter-dialog-content");

        Button apply = new Button("Apply filters", VaadinIcon.CHECK.create(), event -> {
            if (!validIntegerField(reputationMin, 1, 10000)
                    || !validIntegerField(reputationMax, 1, 10000)
                    || !validRange("Reputation", reputationMin.getValue(), reputationMax.getValue())
                    || !validRange("Balance", balanceMin.value(), balanceMax.value())
                    || !validRange("Transfer budget", transferMin.value(), transferMax.value())
                    || !validRange("Payroll budget", payrollMin.value(), payrollMax.value())) {
                return;
            }
            clubFilter = new ClubFilterCriteria(
                    name.getValue(),
                    competition.getValue(),
                    nation.getValue(),
                    reputationMin.getValue(), reputationMax.getValue(),
                    balanceMin.value(), balanceMax.value(),
                    transferMin.value(), transferMax.value(),
                    payrollMin.value(), payrollMax.value());
            showClubs();
            dialog.close();
        });
        apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button clear = new Button("Clear", VaadinIcon.TRASH.create(), event -> {
            clubFilter = ClubFilterCriteria.empty();
            showClubs();
            updateStatus(null);
            dialog.close();
        });
        Button cancel = new Button("Cancel", VaadinIcon.CLOSE_SMALL.create(), event -> dialog.close());

        dialog.add(dialogContent);
        dialog.getFooter().add(clear, cancel, apply);
        dialog.open();
    }

    private void openCompetitionFilterDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Competition filter");
        dialog.setWidth("1280px");
        dialog.setMaxWidth("calc(100vw - 32px)");
        dialog.setMaxHeight("calc(100vh - 32px)");
        dialog.getElement().getThemeList().add("professional-dialog");
        dialog.getElement().getThemeList().add("filter-dialog");

        ComboBox<String> name = comboBox("Name", competitions.findNames(), competitionFilter.name());
        ComboBox<String> nation = comboBox("Nation", competitions.findNations(), competitionFilter.nation());
        ComboBox<String> gender = comboBox("Gender", competitions.findGenders(), competitionFilter.gender());
        IntegerField reputationMin = intField("Reputation min", competitionFilter.reputationMin(), 1, 10000);
        IntegerField reputationMax = intField("Reputation max", competitionFilter.reputationMax(), 1, 10000);

        FormLayout filters = new FormLayout(
                name, nation,
                reputationMin, reputationMax,
                gender);
        filters.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("720px", 2));
        filters.addClassName("filter-form");

        Div dialogContent = new Div(filters);
        dialogContent.setWidthFull();
        dialogContent.addClassName("dialog-content");
        dialogContent.addClassName("filter-dialog-content");

        Button apply = new Button("Apply filters", VaadinIcon.CHECK.create(), event -> {
            if (!validIntegerField(reputationMin, 1, 10000)
                    || !validIntegerField(reputationMax, 1, 10000)
                    || !validRange("Reputation", reputationMin.getValue(), reputationMax.getValue())) {
                return;
            }
            competitionFilter = new CompetitionFilterCriteria(
                    name.getValue(),
                    nation.getValue(),
                    reputationMin.getValue(),
                    reputationMax.getValue(),
                    gender.getValue());
            showCompetitions();
            dialog.close();
        });
        apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button clear = new Button("Clear", VaadinIcon.TRASH.create(), event -> {
            competitionFilter = CompetitionFilterCriteria.empty();
            showCompetitions();
            updateStatus(null);
            dialog.close();
        });
        Button cancel = new Button("Cancel", VaadinIcon.CLOSE_SMALL.create(), event -> dialog.close());

        dialog.add(dialogContent);
        dialog.getFooter().add(clear, cancel, apply);
        dialog.open();
    }

    private void updateStatus(DatabaseLoadAllService.LoadAllResult result) {
        if (result == null) {
            renderStatus(players.countPlayers(), clubs.countClubs(), competitions.countCompetitions(), null, null);
            return;
        }
        renderStatus(result.players(), result.clubs(), result.competitions(), result.pid(), result.gameDate());
    }

    private void renderStatus(long playerCount, long clubCount, long competitionCount, Integer pid, String gameDate) {
        status.removeAll();

        boolean loaded = pid != null || (gameDate != null && !gameDate.isBlank());
        Span freshness = new Span(loaded ? "Loaded" : "Ready");
        freshness.addClassName("status-live");
        status.add(freshness);

        if (loaded) {
            StringBuilder metaText = new StringBuilder();
            if (gameDate != null && !gameDate.isBlank()) {
                metaText.append(nullSafe(gameDate));
            }
            if (pid != null) {
                if (!metaText.isEmpty()) {
                    metaText.append(" · ");
                }
                metaText.append("PID ").append(pid);
            }
            Span meta = new Span(metaText.toString());
            meta.addClassName("status-meta");
            status.add(meta);
        }

        Div chips = new Div(
                statusChip("Players", playerCount),
                statusChip("Clubs", clubCount),
                statusChip("Competitions", competitionCount));
        chips.addClassName("status-chips");
        status.add(chips);
    }

    private void renderFilteredStatus(String entityLabel, int filteredCount, long totalCount) {
        status.removeAll();
        Span freshness = new Span("Filtered");
        freshness.addClassName("status-live");
        status.add(freshness);
        Span meta = new Span(filteredCount + " of " + totalCount + " " + entityLabel.toLowerCase());
        meta.addClassName("status-meta");
        status.add(meta);
    }

    private static Div statusChip(String label, long value) {
        Span labelText = new Span(label);
        Span valueText = new Span(String.valueOf(value));
        valueText.addClassName("status-chip-value");
        Div chip = new Div(labelText, valueText);
        chip.addClassName("status-chip");
        return chip;
    }

    private void syncQuickFiltersFromCriteria() {
        syncingQuickFilters = true;
        try {
            quickName.setValue(nullSafeValue(playerFilter.name()));
            List<String> clubs = players.findClubs();
            quickClub.setItems(clubs);
            String club = meaningfulText(playerFilter.club());
            if (club != null) {
                quickClub.setValue(club);
            } else {
                quickClub.clear();
            }
            quickCaMin.setValue(meaningfulMinValue(playerFilter.caMin()));
            quickAgeMax.setValue(playerFilter.ageMax());
        } finally {
            syncingQuickFilters = false;
        }
    }

    private void applyQuickFilters() {
        String name = meaningfulText(quickName.getValue());
        String club = meaningfulText(quickClub.getValue());
        Integer caMin = quickCaMin.getValue();
        Integer ageMax = quickAgeMax.getValue();
        playerFilter = copyPlayerFilter(
                playerFilter,
                name == null ? "" : name,
                club == null ? "" : club,
                playerFilter.ageMin(),
                ageMax,
                caMin,
                playerFilter.caMax(),
                playerFilter.paMin(),
                playerFilter.paMax(),
                playerFilter.salaryMax(),
                playerFilter.playingNation(),
                playerFilter.playingCompetition(),
                playerFilter.nationality(),
                playerFilter.positionMinimums(),
                playerFilter.attributeMinimums());
        showPlayers();
    }

    private void patchPlayerFilter(String name, String club, Integer ignored, Boolean clearAge) {
        playerFilter = copyPlayerFilter(
                playerFilter,
                name != null ? name : playerFilter.name(),
                club != null ? club : playerFilter.club(),
                Boolean.TRUE.equals(clearAge) ? null : playerFilter.ageMin(),
                Boolean.TRUE.equals(clearAge) ? null : playerFilter.ageMax(),
                playerFilter.caMin(),
                playerFilter.caMax(),
                playerFilter.paMin(),
                playerFilter.paMax(),
                playerFilter.salaryMax(),
                playerFilter.playingNation(),
                playerFilter.playingCompetition(),
                playerFilter.nationality(),
                playerFilter.positionMinimums(),
                playerFilter.attributeMinimums());
        showPlayers();
    }

    private void clearPlayingNation() {
        playerFilter = copyPlayerFilter(
                playerFilter, playerFilter.name(), playerFilter.club(),
                playerFilter.ageMin(), playerFilter.ageMax(),
                playerFilter.caMin(), playerFilter.caMax(),
                playerFilter.paMin(), playerFilter.paMax(),
                playerFilter.salaryMax(),
                "", playerFilter.playingCompetition(), playerFilter.nationality(),
                playerFilter.positionMinimums(), playerFilter.attributeMinimums());
        showPlayers();
    }

    private void clearPlayingCompetition() {
        playerFilter = copyPlayerFilter(
                playerFilter, playerFilter.name(), playerFilter.club(),
                playerFilter.ageMin(), playerFilter.ageMax(),
                playerFilter.caMin(), playerFilter.caMax(),
                playerFilter.paMin(), playerFilter.paMax(),
                playerFilter.salaryMax(),
                playerFilter.playingNation(), "", playerFilter.nationality(),
                playerFilter.positionMinimums(), playerFilter.attributeMinimums());
        showPlayers();
    }

    private void clearNationality() {
        playerFilter = copyPlayerFilter(
                playerFilter, playerFilter.name(), playerFilter.club(),
                playerFilter.ageMin(), playerFilter.ageMax(),
                playerFilter.caMin(), playerFilter.caMax(),
                playerFilter.paMin(), playerFilter.paMax(),
                playerFilter.salaryMax(),
                playerFilter.playingNation(), playerFilter.playingCompetition(), "",
                playerFilter.positionMinimums(), playerFilter.attributeMinimums());
        showPlayers();
    }

    private void clearCaFilter() {
        playerFilter = copyPlayerFilter(
                playerFilter, playerFilter.name(), playerFilter.club(),
                playerFilter.ageMin(), playerFilter.ageMax(),
                null, null,
                playerFilter.paMin(), playerFilter.paMax(),
                playerFilter.salaryMax(),
                playerFilter.playingNation(), playerFilter.playingCompetition(), playerFilter.nationality(),
                playerFilter.positionMinimums(), playerFilter.attributeMinimums());
        showPlayers();
    }

    private void clearPaFilter() {
        playerFilter = copyPlayerFilter(
                playerFilter, playerFilter.name(), playerFilter.club(),
                playerFilter.ageMin(), playerFilter.ageMax(),
                playerFilter.caMin(), playerFilter.caMax(),
                null, null,
                playerFilter.salaryMax(),
                playerFilter.playingNation(), playerFilter.playingCompetition(), playerFilter.nationality(),
                playerFilter.positionMinimums(), playerFilter.attributeMinimums());
        showPlayers();
    }

    private void clearSalaryMax() {
        playerFilter = copyPlayerFilter(
                playerFilter, playerFilter.name(), playerFilter.club(),
                playerFilter.ageMin(), playerFilter.ageMax(),
                playerFilter.caMin(), playerFilter.caMax(),
                playerFilter.paMin(), playerFilter.paMax(),
                null,
                playerFilter.playingNation(), playerFilter.playingCompetition(), playerFilter.nationality(),
                playerFilter.positionMinimums(), playerFilter.attributeMinimums());
        showPlayers();
    }

    private void clearPositions() {
        playerFilter = copyPlayerFilter(
                playerFilter, playerFilter.name(), playerFilter.club(),
                playerFilter.ageMin(), playerFilter.ageMax(),
                playerFilter.caMin(), playerFilter.caMax(),
                playerFilter.paMin(), playerFilter.paMax(),
                playerFilter.salaryMax(),
                playerFilter.playingNation(), playerFilter.playingCompetition(), playerFilter.nationality(),
                Map.of(), playerFilter.attributeMinimums());
        showPlayers();
    }

    private void clearAttributes() {
        playerFilter = copyPlayerFilter(
                playerFilter, playerFilter.name(), playerFilter.club(),
                playerFilter.ageMin(), playerFilter.ageMax(),
                playerFilter.caMin(), playerFilter.caMax(),
                playerFilter.paMin(), playerFilter.paMax(),
                playerFilter.salaryMax(),
                playerFilter.playingNation(), playerFilter.playingCompetition(), playerFilter.nationality(),
                playerFilter.positionMinimums(), Map.of());
        showPlayers();
    }

    private static PlayerFilterCriteria copyPlayerFilter(
            PlayerFilterCriteria source,
            String name,
            String club,
            Integer ageMin,
            Integer ageMax,
            Integer caMin,
            Integer caMax,
            Integer paMin,
            Integer paMax,
            Long salaryMax,
            String playingNation,
            String playingCompetition,
            String nationality,
            Map<String, Integer> positionMinimums,
            Map<String, Integer> attributeMinimums) {
        return new PlayerFilterCriteria(
                name,
                source.gender(),
                playingNation,
                playingCompetition,
                club,
                ageMin,
                ageMax,
                source.heightMin(),
                source.heightMax(),
                nationality,
                source.currentReputationMin(),
                source.currentReputationMax(),
                source.homeReputationMin(),
                source.homeReputationMax(),
                source.worldReputationMin(),
                source.worldReputationMax(),
                caMin,
                caMax,
                paMin,
                paMax,
                source.contractEndDateFrom(),
                source.contractEndDateTo(),
                source.askingPriceMin(),
                source.askingPriceMax(),
                salaryMax,
                positionMinimums,
                attributeMinimums);
    }

    private static String meaningfulText(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean meaningfulMin(Integer value) {
        return value != null && value > 1;
    }

    private static Integer meaningfulMinValue(Integer value) {
        return meaningfulMin(value) ? value : null;
    }

    private static String rangeLabel(Integer min, Integer max) {
        if (min != null && max != null) {
            return min + "–" + max;
        }
        if (min != null) {
            return "≥ " + min;
        }
        if (max != null) {
            return "≤ " + max;
        }
        return "";
    }

    private void setFilterActive(boolean active) {
        filterButton.setText(active ? "Filter active" : "Filter");
        filterButton.getElement().getClassList().set("has-filter", active);
    }

    private Long fromDisplayPounds(Long pounds) {
        if (pounds == null) {
            return null;
        }
        MoneyCurrency selected = currency == null ? MoneyCurrency.POUND : currency;
        return MoneyDisplay.convert(pounds, selected);
    }

    private Long toFilterPounds(Long displayed) {
        if (displayed == null) {
            return null;
        }
        return MoneyDisplay.toBasePounds(displayed, currency);
    }

    private static String display(Object value) {
        return value == null ? "" : Objects.toString(value);
    }

    private String displayColumn(String column, Object value) {
        if ("SALARY_WEEKLY_RAW".equals(column)) {
            return salaryWeeklyDisplay(value);
        }
        return MONEY_COLUMNS.contains(column) ? moneyDisplay(value) : display(value);
    }

    private String salaryWeeklyDisplay(Object value) {
        Long pounds = sortableLong(value);
        if (pounds == null) {
            return "";
        }
        return MoneyDisplay.format(pounds, currency);
    }

    private String moneyDisplay(Object value) {
        Long pounds = sortableLong(value);
        if (pounds == null) {
            return "";
        }
        return MoneyDisplay.format(pounds, currency);
    }


    private static String heightDisplay(PlayerEntity player) {
        Integer cm = player.getHeightCm();
        if (cm == null || cm <= 0) {
            return "";
        }
        int totalInches = (int) Math.round(cm / 2.54);
        return cm + " cm (" + (totalInches / 12) + "'" + (totalInches % 12) + "\")";
    }

    private static FormLayout detailLayout(List<DetailField> fields) {
        FormLayout layout = new FormLayout();
        layout.addClassName("detail-grid");
        layout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("720px", 2));
        fields.stream()
                .map(field -> detailField(field.label(), field.value()))
                .forEach(layout::add);
        return layout;
    }

    private static Div detailField(String label, Object value) {
        Span labelText = new Span(label);
        labelText.addClassName("detail-label");
        Span valueText = new Span(display(value));
        valueText.addClassName("detail-value");
        Div field = new Div(labelText, valueText);
        field.addClassName("detail-field");
        return field;
    }

    private static Div positionField(PlayerEntity player) {
        Div field = new Div();
        field.addClassName("position-pitch");

        addFieldLine(field, "50%", "0", "100%", "2px", "#d1fae580", "translateY(-1px)");
        addFieldLine(field, "50%", "50%", "68px", "68px", "#d1fae580", "translate(-50%, -50%)");
        addPenaltyBox(field, "top");
        addPenaltyBox(field, "bottom");

        field.add(
                positionFieldRow(positionRow(null, pos(player, "ST", "Striker"), null)),
                positionFieldRow(List.of(pos(player, "AML", "AttackingMidfielderLeft"), pos(player, "AMC", "AttackingMidfielderCentral"), pos(player, "AMR", "AttackingMidfielderRight"))),
                positionFieldRow(List.of(pos(player, "ML", "MidfielderLeft"), pos(player, "MC", "MidfielderCentral"), pos(player, "MR", "MidfielderRight"))),
                positionFieldRow(List.of(pos(player, "WBL", "WingBackLeft"), pos(player, "DMC", "DefensiveMidfielder"), pos(player, "WBR", "WingBackRight"))),
                positionFieldRow(List.of(pos(player, "DL", "DefenderLeft"), pos(player, "DC", "DefenderCentral"), pos(player, "DR", "DefenderRight"))),
                positionFieldRow(positionRow(null, pos(player, "GK", "Goalkeeper"), null))
        );
        return field;
    }

    private static Div positionFilterField(Map<String, PositionLevel> selectedPositions) {
        Div field = emptyField();
        field.add(
                positionFilterRow(blank(), positionFilterTile("ST", "Striker", "STRIKER", selectedPositions), blank()),
                positionFilterRow(
                        positionFilterTile("AML", "Attacking Midfielder Left", "ATTACKING_MIDFIELDER_LEFT", selectedPositions),
                        positionFilterTile("AMC", "Attacking Midfielder Central", "ATTACKING_MIDFIELDER_CENTRAL", selectedPositions),
                        positionFilterTile("AMR", "Attacking Midfielder Right", "ATTACKING_MIDFIELDER_RIGHT", selectedPositions)),
                positionFilterRow(
                        positionFilterTile("ML", "Midfielder Left", "MIDFIELDER_LEFT", selectedPositions),
                        positionFilterTile("MC", "Midfielder Central", "MIDFIELDER_CENTRAL", selectedPositions),
                        positionFilterTile("MR", "Midfielder Right", "MIDFIELDER_RIGHT", selectedPositions)),
                positionFilterRow(
                        positionFilterTile("WBL", "Wing Back Left", "WING_BACK_LEFT", selectedPositions),
                        positionFilterTile("DMC", "Defensive Midfielder", "DEFENSIVE_MIDFIELDER", selectedPositions),
                        positionFilterTile("WBR", "Wing Back Right", "WING_BACK_RIGHT", selectedPositions)),
                positionFilterRow(
                        positionFilterTile("DL", "Defender Left", "DEFENDER_LEFT", selectedPositions),
                        positionFilterTile("DC", "Defender Central", "DEFENDER_CENTRAL", selectedPositions),
                        positionFilterTile("DR", "Defender Right", "DEFENDER_RIGHT", selectedPositions)),
                positionFilterRow(blank(), positionFilterTile("GK", "Goalkeeper", "GOALKEEPER", selectedPositions), blank())
        );
        return field;
    }

    private static Div emptyField() {
        Div field = new Div();
        field.addClassName("position-pitch");
        field.addClassName("position-filter-pitch");
        addFieldLine(field, "50%", "0", "100%", "2px", "#d1fae580", "translateY(-1px)");
        addFieldLine(field, "50%", "50%", "76px", "76px", "#d1fae580", "translate(-50%, -50%)");
        addPenaltyBox(field, "top");
        addPenaltyBox(field, "bottom");
        return field;
    }

    private static List<PositionTile> positionRow(PositionTile left, PositionTile center, PositionTile right) {
        List<PositionTile> row = new ArrayList<>();
        row.add(left);
        row.add(center);
        row.add(right);
        return row;
    }

    private static PositionTile pos(PlayerEntity player, String label, String fieldName) {
        String column = PlayerColumnNames.toColumnName(fieldName).toUpperCase();
        return new PositionTile(label, displayName(fieldName), player.getColumnValue(column));
    }

    private static Div positionFieldRow(List<PositionTile> positions) {
        Div row = new Div();
        row.addClassName("position-row");
        row.getStyle()
                .set("position", "relative")
                .set("z-index", "1")
                .set("display", "grid")
                .set("grid-template-columns", "repeat(3, minmax(0, 1fr))")
                .set("gap", "6px")
                .set("align-items", "center");
        positions.forEach(position -> row.add(position == null ? new Div() : positionFieldTile(position)));
        return row;
    }

    private static Div positionFilterRow(Component... positions) {
        Div row = new Div();
        row.addClassName("position-row");
        row.getStyle()
                .set("position", "relative")
                .set("z-index", "1")
                .set("display", "grid")
                .set("grid-template-columns", "repeat(3, minmax(0, 1fr))")
                .set("gap", "7px")
                .set("align-items", "center");
        row.add(positions);
        return row;
    }

    private static Div blank() {
        return new Div();
    }

    private static Button positionFilterTile(
            String shortName,
            String fullName,
            String column,
            Map<String, PositionLevel> selectedPositions) {
        PositionLevel initial = selectedPositions.getOrDefault(column, PositionLevel.CANNOT);
        Button button = new Button(filterPositionLabel(shortName, initial));
        button.addClassName("position-filter-tile");
        button.getElement().setProperty("title", fullName);
        button.setWidthFull();
        button.getStyle()
                .set("min-width", "0")
                .set("min-height", "38px")
                .set("padding", "6px 4px")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("font-weight", "800")
                .set("line-height", "1.1")
                .set("white-space", "normal");
        applyPositionColor(button, initial);
        button.addClickListener(event -> {
            PositionLevel next = selectedPositions.getOrDefault(column, PositionLevel.CANNOT).next();
            selectedPositions.put(column, next);
            button.setText(filterPositionLabel(shortName, next));
            applyPositionColor(button, next);
        });
        return button;
    }

    private static Div positionFieldTile(PositionTile position) {
        Span label = new Span(position.shortName());
        label.getStyle().set("font-weight", "800").set("line-height", "1");
        Span value = new Span(display(position.value()) + " - " + PositionTextFormatter.positionLevelText(position.value()));
        value.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("line-height", "1.15")
                .set("text-align", "center");
        Div tile = new Div(label, value);
        tile.addClassName("position-tile");
        PositionLevel level = PositionLevel.fromScore(position.value());
        tile.getElement().setProperty("title", position.fullName());
        tile.getStyle()
                .set("min-width", "0")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("gap", "3px")
                .set("padding", "5px 4px")
                .set("min-height", "42px")
                .set("border-radius", "8px")
                .set("background", level.color)
                .set("color", level.textColor)
                .set("border", "1px solid rgba(255,255,255,.55)")
                .set("box-shadow", "0 1px 3px rgba(0,0,0,.22)");
        return tile;
    }

    private static void addFieldLine(Div field, String top, String left, String width, String height, String color, String transform) {
        Div line = new Div();
        line.getStyle()
                .set("position", "absolute")
                .set("top", top)
                .set("left", left)
                .set("width", width)
                .set("height", height)
                .set("border", "2px solid " + color)
                .set("border-radius", "999px")
                .set("transform", transform)
                .set("box-sizing", "border-box")
                .set("pointer-events", "none");
        field.add(line);
    }

    private static void addPenaltyBox(Div field, String side) {
        Div box = new Div();
        box.getStyle()
                .set("position", "absolute")
                .set(side, "-2px")
                .set("left", "50%")
                .set("width", "44%")
                .set("height", "15%")
                .set("border", "2px solid #d1fae580")
                .set("border-" + side, "0")
                .set("transform", "translateX(-50%)")
                .set("box-sizing", "border-box")
                .set("pointer-events", "none");
        field.add(box);
    }

    private static void renderAttributeColumns(
            Div container,
            PlayerEntity player,
            boolean showGoalkeeping,
            Map<String, String> rolePriorities) {
        container.removeAll();
        if (!rolePriorities.isEmpty()) {
            container.add(roleFocusPanel(player, rolePriorities));
        }
        Div columns = new Div();
        columns.addClassName("attribute-columns");
        PlayerAttributeCatalog.categories(showGoalkeeping).forEach(category -> columns.add(attributeCategory(player, category, rolePriorities)));
        if (!rolePriorities.isEmpty()) {
            Span allTitle = new Span("All attributes");
            allTitle.addClassName("attribute-all-title");
            container.add(allTitle);
        }
        container.add(columns);
    }

    private static Div roleFocusPanel(PlayerEntity player, Map<String, String> rolePriorities) {
        Div primary = new Div();
        primary.addClassName("role-focus-group");
        Span primaryTitle = new Span("Primary for role");
        primaryTitle.addClassName("role-focus-title");
        primary.add(primaryTitle);

        Div secondary = new Div();
        secondary.addClassName("role-focus-group");
        Span secondaryTitle = new Span("Secondary for role");
        secondaryTitle.addClassName("role-focus-title");
        secondary.add(secondaryTitle);

        Map<String, PlayerAttributeCatalog.AttributeDefinition> byColumn = new LinkedHashMap<>();
        PlayerAttributeCatalog.categories(true).forEach(category -> {
            for (PlayerAttributeCatalog.AttributeDefinition attribute : category.attributes()) {
                byColumn.putIfAbsent(attribute.columnName().toLowerCase(), attribute);
            }
        });

        List<Map.Entry<String, String>> ordered = rolePriorities.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<String, String> entry) -> !"primary".equals(entry.getValue()))
                        .thenComparing(Map.Entry::getKey))
                .toList();
        for (Map.Entry<String, String> entry : ordered) {
            PlayerAttributeCatalog.AttributeDefinition definition = byColumn.get(entry.getKey().toLowerCase());
            if (definition == null) {
                continue;
            }
            Div row = attributeRow(definition.displayName(), attributeValue(player, definition.columnName()), entry.getValue());
            row.addClassName("role-focus-row");
            if ("primary".equals(entry.getValue())) {
                primary.add(row);
            } else if ("secondary".equals(entry.getValue())) {
                secondary.add(row);
            }
        }

        Div panel = new Div();
        panel.addClassName("attribute-role-focus");
        if (primary.getChildren().count() > 1) {
            panel.add(primary);
        }
        if (secondary.getChildren().count() > 1) {
            panel.add(secondary);
        }
        return panel;
    }

    private static Div attributeCategory(
            PlayerEntity player,
            PlayerAttributeCatalog.AttributeCategory category,
            Map<String, String> rolePriorities) {
        Span title = new Span(category.name());
        title.addClassName("attribute-category-title");
        Div column = new Div(title);
        column.addClassName("attribute-category");
        for (PlayerAttributeCatalog.AttributeDefinition attribute : category.attributes()) {
            Object value = attributeValue(player, attribute.columnName());
            column.add(attributeRow(attribute.displayName(), value, rolePriority(rolePriorities, attribute.columnName())));
        }
        return column;
    }

    private static Div attributeRow(String label, Object value, String rolePriority) {
        Span labelText = new Span(label);
        labelText.addClassName("attribute-label");
        Span valueText = new Span(display(value));
        valueText.addClassName("attribute-value");
        valueText.getStyle().set("color", scoreColor(value));

        Div fill = new Div();
        fill.addClassName("attr-meter-fill");
        fill.getStyle().set("background", scoreColor(value));
        Long score = sortableLong(value);
        if (score != null) {
            int pct = (int) Math.max(0, Math.min(100, Math.round(score * 5.0)));
            fill.getStyle().set("width", pct + "%");
        } else {
            fill.getStyle().set("width", "0%");
        }
        Div meter = new Div(fill);
        meter.addClassName("attr-meter");

        Div head = new Div(labelText, valueText);
        head.addClassName("attribute-head");
        Div row = new Div(head, meter);
        row.addClassName("attribute-row");
        if ("primary".equals(rolePriority)) {
            row.addClassName("role-primary");
        } else if ("secondary".equals(rolePriority)) {
            row.addClassName("role-secondary");
        }
        return row;
    }

    private static Object attributeValue(PlayerEntity player, String columnName) {
        return columnName == null ? null : player.getColumnValue(columnName);
    }

    private static String rolePriority(Map<String, String> rolePriorities, String columnName) {
        return columnName == null ? null : rolePriorities.get(columnName.toLowerCase());
    }

    private static String scoreColor(Object value) {
        Long score = sortableLong(value);
        if (score == null) {
            return "var(--lumo-secondary-text-color)";
        }
        if (score <= 5) {
            return "#dc2626";
        }
        if (score <= 10) {
            return "#ea580c";
        }
        if (score <= 15) {
            return "#ca8a04";
        }
        return "#16a34a";
    }

    private static ComboBox<String> roleComboBox(String label, String phase) {
        ComboBox<String> comboBox = new ComboBox<>(label);
        comboBox.setItems(PlayerRoleAttributeCatalog.roles(phase));
        comboBox.setClearButtonVisible(true);
        comboBox.setWidth("260px");
        return comboBox;
    }

    private static Map<String, String> selectedRolePriorities(ComboBox<String> inPossessionRole, ComboBox<String> outOfPossessionRole) {
        if (inPossessionRole.getValue() != null && !inPossessionRole.getValue().isBlank()) {
            return PlayerRoleAttributeCatalog.priorities(PlayerRoleAttributeCatalog.IN_POSSESSION, inPossessionRole.getValue());
        }
        if (outOfPossessionRole.getValue() != null && !outOfPossessionRole.getValue().isBlank()) {
            return PlayerRoleAttributeCatalog.priorities(PlayerRoleAttributeCatalog.OUT_OF_POSSESSION, outOfPossessionRole.getValue());
        }
        return Map.of();
    }

    private static boolean isGoalkeeper(PlayerEntity player) {
        Integer goalkeeper = player.getGoalkeeper();
        return goalkeeper != null && goalkeeper >= 15;
    }

    private int comparePlayerColumn(PlayerEntity left, PlayerEntity right, PlayerColumn column) {
        if (NUMERIC_SORT_COLUMNS.contains(column.key())) {
            if ("SALARY_WEEKLY_RAW".equals(column.key())) {
                return compareLongs(
                        displayedWeeklySalary(column.value(left)),
                        displayedWeeklySalary(column.value(right)));
            }
            return compareLongs(sortableLong(column.value(left)), sortableLong(column.value(right)));
        }
        return display(column.value(left)).compareToIgnoreCase(display(column.value(right)));
    }

    private Long displayedWeeklySalary(Object value) {
        Long pounds = sortableLong(value);
        return pounds == null ? null : MoneyDisplay.displayedAmount(pounds, currency);
    }

    private static int compareClubColumn(ClubEntity left, ClubEntity right, String column) {
        if (NUMERIC_SORT_COLUMNS.contains(column)) {
            return compareLongs(sortableLong(clubColumnValue(left, column)), sortableLong(clubColumnValue(right, column)));
        }
        return display(clubColumnValue(left, column)).compareToIgnoreCase(display(clubColumnValue(right, column)));
    }

    private static int compareCompetitionColumn(CompetitionEntity left, CompetitionEntity right, String column) {
        if (NUMERIC_SORT_COLUMNS.contains(column)) {
            return compareLongs(sortableLong(competitionColumnValue(left, column)), sortableLong(competitionColumnValue(right, column)));
        }
        return display(competitionColumnValue(left, column)).compareToIgnoreCase(display(competitionColumnValue(right, column)));
    }

    private static int compareLongs(Long left, Long right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return Long.compare(left, right);
    }

    private static Long sortableLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String nullSafeValue(String value) {
        return value == null ? "" : value;
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static boolean sameText(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static String toColumnName(String fieldName) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char ch = fieldName.charAt(i);
            if (Character.isUpperCase(ch)) {
                out.append('_').append(Character.toLowerCase(ch));
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static IntegerField intField(String label, Integer value, int min, int max) {
        IntegerField field = new IntegerField(label);
        field.setMin(min);
        field.setMax(max);
        field.setStep(1);
        field.setClearButtonVisible(true);
        field.setValue(value);
        return field;
    }

    private static ComboBox<String> comboBox(String label, List<String> items, String value) {
        ComboBox<String> comboBox = new ComboBox<>(label);
        comboBox.setItems(items);
        comboBox.setClearButtonVisible(true);
        comboBox.setAllowCustomValue(false);
        if (value != null && !value.isBlank()) {
            comboBox.setValue(value);
        }
        return comboBox;
    }

    private void createAttributeFields(Map<String, IntegerField> attributeFields, Div attributeLayout) {
        if (!attributeFields.isEmpty()) {
            return;
        }
        for (PlayerAttributeCatalog.AttributeCategory category : PlayerAttributeCatalog.filterCategories()) {
            VerticalLayout column = new VerticalLayout();
            column.setPadding(false);
            column.setSpacing(false);
            column.addClassName("attribute-filter-column");
            Span title = new Span(attributeFilterCategoryTitle(category.name()));
            title.getStyle()
                    .set("font-weight", "700")
                    .set("padding-bottom", "6px")
                    .set("border-bottom", "1px solid var(--lumo-contrast-20pct)")
                    .set("margin-bottom", "6px");
            column.add(title);
            for (PlayerAttributeCatalog.AttributeDefinition definition : category.attributes()) {
                String attributeColumn = definition.columnName();
                String key = category.name() + ":" + attributeColumn;
                IntegerField attribute = intField(
                        definition.displayName(),
                        playerFilter.attributeMinimums().get(attributeColumn),
                        1,
                        20);
                attribute.setWidthFull();
                attributeFields.put(key, attribute);
                column.add(attribute);
            }
            attributeLayout.add(column);
        }
    }

    private static boolean validPlayerFilter(
            IntegerField currentRepMin,
            IntegerField currentRepMax,
            IntegerField homeRepMin,
            IntegerField homeRepMax,
            IntegerField worldRepMin,
            IntegerField worldRepMax,
            IntegerField caMin,
            IntegerField caMax,
            IntegerField paMin,
            IntegerField paMax,
            IntegerField heightMin,
            IntegerField heightMax,
            Map<String, IntegerField> attributeFields) {
        for (IntegerField field : List.of(currentRepMin, currentRepMax, homeRepMin, homeRepMax, worldRepMin, worldRepMax)) {
            if (!validIntegerField(field, 1, 10000)) {
                return false;
            }
        }
        for (IntegerField field : List.of(caMin, caMax, paMin, paMax)) {
            if (!validIntegerField(field, 1, 200)) {
                return false;
            }
        }
        for (IntegerField field : List.of(heightMin, heightMax)) {
            if (!validIntegerField(field, 100, 230)) {
                return false;
            }
        }
        for (IntegerField field : attributeFields.values()) {
            if (!validIntegerField(field, 1, 20)) {
                return false;
            }
        }
        return validRange("Current reputation", currentRepMin.getValue(), currentRepMax.getValue())
                && validRange("Home reputation", homeRepMin.getValue(), homeRepMax.getValue())
                && validRange("World reputation", worldRepMin.getValue(), worldRepMax.getValue())
                && validRange("CA", caMin.getValue(), caMax.getValue())
                && validRange("PA", paMin.getValue(), paMax.getValue())
                && validRange("Height", heightMin.getValue(), heightMax.getValue());
    }

    private static boolean validIntegerField(IntegerField field, int min, int max) {
        Integer value = field.getValue();
        if (value == null) {
            return true;
        }
        if (value < min || value > max) {
            Notification.show(field.getLabel() + " must be between " + min + " and " + max, 5000, Notification.Position.TOP_CENTER);
            return false;
        }
        return true;
    }

    private static boolean validRange(String label, Integer min, Integer max) {
        if (min != null && max != null && min > max) {
            Notification.show(label + " min must be less than or equal to max", 5000, Notification.Position.TOP_CENTER);
            return false;
        }
        return true;
    }

    private static boolean validRange(String label, Long min, Long max) {
        if (min != null && max != null && min > max) {
            Notification.show(label + " min must be less than or equal to max", 5000, Notification.Position.TOP_CENTER);
            return false;
        }
        return true;
    }

    private static Object clubColumnValue(ClubEntity club, String column) {
        return switch (column) {
            case "NAME" -> club.getName();
            case "COMPETITION" -> club.getCompetition();
            case "NATION" -> club.getNation();
            case "REPUTATION" -> club.getReputation();
            case "BALANCE" -> club.getBalance();
            case "TRANSFER_BUDGET" -> club.getTransferBudget();
            case "PAYROLL_BUDGET" -> club.getPayrollBudget();
            default -> null;
        };
    }

    private static Object competitionColumnValue(CompetitionEntity competition, String column) {
        return switch (column) {
            case "NAME" -> competition.getName();
            case "NATION" -> competition.getNation();
            case "REPUTATION" -> competition.getReputation();
            case "GENDER" -> competition.getGender();
            default -> null;
        };
    }

    private static Integer defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static Map<String, Integer> selectedPositionMinimums(Map<String, PositionLevel> selectedPositions) {
        Map<String, Integer> out = new LinkedHashMap<>();
        selectedPositions.forEach((column, level) -> {
            if (level.minimum > 1) {
                out.put(column, level.minimum);
            }
        });
        return out;
    }

    private static Map<String, Integer> selectedAttributeMinimums(Map<String, IntegerField> attributeFields) {
        Map<String, Integer> out = new LinkedHashMap<>();
        attributeFields.forEach((key, field) -> {
            Integer value = defaultInt(field.getValue(), 1);
            if (value != null && value > 1) {
                String column = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
                out.merge(column, value, Math::max);
            }
        });
        return out;
    }

    private static String filterPositionLabel(String shortName, PositionLevel level) {
        return shortName + "\n" + level.label;
    }

    private static String displayName(String fieldName) {
        return toColumnName(fieldName).replace('_', ' ');
    }

    private static String attributeFilterCategoryTitle(String categoryName) {
        if (PlayerAttributeCatalog.GOALKEEPING.equals(categoryName)) {
            return "Goalkeeper";
        }
        if ("Hidden Attributes".equals(categoryName)) {
            return "Hidden";
        }
        return categoryName;
    }

    private static void applyPositionColor(Button button, PositionLevel level) {
        button.getStyle()
                .set("background", level.color)
                .set("color", level.textColor)
                .set("border", "1px solid var(--lumo-contrast-20pct)");
    }

    private record PlayerColumn(String key, String header, Function<PlayerEntity, Object> valueProvider) {
        private Object value(PlayerEntity player) {
            return valueProvider.apply(player);
        }
    }

    private record GridColumn(String key, String header) {
    }

    private record DetailField(String label, Object value) {
    }

    private record PositionTile(String shortName, String fullName, Object value) {
    }

    private enum PositionLevel {
        CANNOT("Cannot play", 1, "#e5e7eb", "#111827"),
        CAN("Can play", 5, "#dc2626", "#ffffff"),
        COMPETENT("Is competent at", 9, "#f97316", "#111827"),
        ACCOMPLISHED("Is accomplished at", 15, "#fde047", "#111827"),
        NATURAL("Is natural at", 18, "#16a34a", "#ffffff");

        private final String label;
        private final int minimum;
        private final String color;
        private final String textColor;

        PositionLevel(String label, int minimum, String color, String textColor) {
            this.label = label;
            this.minimum = minimum;
            this.color = color;
            this.textColor = textColor;
        }

        private PositionLevel next() {
            PositionLevel[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        private static PositionLevel fromMinimum(Integer minimum) {
            if (minimum == null || minimum <= 1) {
                return CANNOT;
            }
            for (PositionLevel level : values()) {
                if (level.minimum == minimum) {
                    return level;
                }
            }
            return CANNOT;
        }

        private static PositionLevel fromScore(Object scoreValue) {
            Long score = sortableLong(scoreValue);
            if (score == null || score <= 4) {
                return CANNOT;
            }
            if (score <= 8) {
                return CAN;
            }
            if (score <= 14) {
                return COMPETENT;
            }
            if (score <= 17) {
                return ACCOMPLISHED;
            }
            return NATURAL;
        }
    }

    private static final class LongField {
        private final com.vaadin.flow.component.textfield.NumberField field;

        private LongField(String label, Long value) {
            field = new com.vaadin.flow.component.textfield.NumberField(label);
            field.setMin(0);
            field.setStep(1000);
            field.setClearButtonVisible(true);
            field.setValue(value == null ? null : value.doubleValue());
        }

        private com.vaadin.flow.component.textfield.NumberField field() {
            return field;
        }

        private Long value() {
            return field.getValue() == null ? null : field.getValue().longValue();
        }
    }
}
