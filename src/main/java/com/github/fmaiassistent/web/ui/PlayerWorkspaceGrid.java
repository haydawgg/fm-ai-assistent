package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;

import java.util.List;

/**
 * Deep presentation seam for the player grid.
 *
 * <p>The desk supplies the visible column schema and current club filter;
 * this module owns the cell renderers, loan row semantics, money-aware
 * sorting, and column rebuild policy.</p>
 */
final class PlayerWorkspaceGrid {
    private final Grid<PlayerEntity> grid;
    private final MoneyCurrency currency;
    private boolean columnsBuilt;
    private String columnsSignature = "";

    PlayerWorkspaceGrid(Grid<PlayerEntity> grid, MoneyCurrency currency) {
        this.grid = grid;
        this.currency = currency;
    }

    void configure(List<PlayerWorkspaceColumns.Column> columns, boolean allMode, String filterClub) {
        String signature = columns.stream().map(PlayerWorkspaceColumns.Column::key).reduce((left, right) -> left + "," + right).orElse("");
        if (!columnsBuilt || !columnsSignature.equals(signature)) {
            grid.removeAllColumns();
            grid.setPartNameGenerator(player -> rowPartName(player, filterClub));
            for (PlayerWorkspaceColumns.Column column : columns) {
                Grid.Column<PlayerEntity> gridColumn;
                if ("NAME".equals(column.key())) {
                    gridColumn = grid.addColumn(new ComponentRenderer<>(this::playerNameCell))
                            .setWidth(columnWidth(column.key()))
                            .setFlexGrow(0);
                } else if ("CA".equals(column.key()) || "PA".equals(column.key())) {
                    gridColumn = grid.addColumn(new ComponentRenderer<>(player -> abilityCell(column.value(player))))
                            .setWidth(columnWidth(column.key()))
                            .setFlexGrow(0);
                } else {
                    gridColumn = grid.addColumn(player -> displayColumn(column.key(), column.value(player)))
                            .setWidth(columnWidth(column.key()))
                            .setFlexGrow(0);
                }
                gridColumn
                        .setKey(column.key())
                        .setHeader(column.header())
                        .setResizable(true)
                        .setComparator((left, right) -> compare(left, right, column))
                        .setSortable(true);
                if ("NAME".equals(column.key())) {
                    gridColumn.setFrozen(true);
                }
            }
            columnsBuilt = true;
            columnsSignature = signature;
        }
        grid.setPartNameGenerator(player -> rowPartName(player, filterClub));
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
        Span text = new Span(value == null ? "—" : display(value));
        text.addClassName("ability-value");
        Div cell = new Div(text);
        cell.addClassName("ability-cell");
        String tone = abilityTone(value);
        if (tone != null) {
            cell.addClassName(tone);
        }
        return cell;
    }

    private String displayColumn(String column, Object value) {
        return PlayerWorkspaceFormatting.column(column, value, currency);
    }

    private int compare(PlayerEntity left, PlayerEntity right, PlayerWorkspaceColumns.Column column) {
        if (PlayerWorkspaceColumns.NUMERIC_SORT_COLUMNS.contains(column.key())) {
            if ("SALARY_WEEKLY_RAW".equals(column.key())) {
                return PlayerWorkspaceFormatting.compareLongs(
                        displayedWeeklySalary(column.value(left)),
                        displayedWeeklySalary(column.value(right)));
            }
            if ("AVERAGE_RATING".equals(column.key())) {
                return compareDoubles(
                        PlayerWorkspaceFormatting.sortableDouble(column.value(left)),
                        PlayerWorkspaceFormatting.sortableDouble(column.value(right)));
            }
            return PlayerWorkspaceFormatting.compareLongs(
                    PlayerWorkspaceFormatting.sortableLong(column.value(left)),
                    PlayerWorkspaceFormatting.sortableLong(column.value(right)));
        }
        return display(column.value(left)).compareToIgnoreCase(display(column.value(right)));
    }

    private Long displayedWeeklySalary(Object value) {
        Long pounds = PlayerWorkspaceFormatting.sortableLong(value);
        return pounds == null ? null : MoneyDisplay.displayedAmount(pounds, currency);
    }

    private static int compareDoubles(Double left, Double right) {
        if (left == null && right == null) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        return Double.compare(left, right);
    }

    private static String rowPartName(PlayerEntity player, String filterClub) {
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

    private static String abilityTone(Object value) {
        Long score = PlayerWorkspaceFormatting.sortableLong(value);
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

    private static String display(Object value) {
        return PlayerWorkspaceFormatting.display(value);
    }

    private static String columnWidth(String key) {
        return switch (key) {
            case "NAME" -> "220px";
            case "AGE" -> "72px";
            case "HEIGHT_CM" -> "105px";
            case "NATIONALITY" -> "130px";
            case "CLUB", "PLAYING_CLUB" -> "160px";
            case "POSITION" -> "100px";
            case "CA", "PA" -> "72px";
            case "APPEARANCES", "STARTS", "GOALS", "ASSISTS" -> "82px";
            case "MINUTES" -> "95px";
            case "AVERAGE_RATING" -> "88px";
            case "SALARY_WEEKLY_RAW" -> "125px";
            case "ASKING_PRICE" -> "135px";
            case "CONTRACT_END_DATE" -> "125px";
            case "TRANSFER_LISTED", "LISTED_FOR_LOAN", "TRANSFER_AGREED", "INJURED" -> "125px";
            case "FUTURE_TRANSFER_CLUB", "FUTURE_TRANSFER_DATE", "FUTURE_TRANSFER_CONTRACT_END_DATE" -> "180px";
            case "INJURY" -> "190px";
            case "TRAITS" -> "220px";
            default -> "145px";
        };
    }

    private static boolean sameText(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
