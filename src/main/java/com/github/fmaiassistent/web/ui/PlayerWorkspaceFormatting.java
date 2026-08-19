package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.domain.enums.MoneyCurrency;

import java.util.Objects;

/** Pure display and sort formatting rules for the player workspace. */
final class PlayerWorkspaceFormatting {
    private PlayerWorkspaceFormatting() {
    }

    static String display(Object value) {
        return value == null ? "" : Objects.toString(value);
    }

    static String column(String column, Object value, MoneyCurrency currency) {
        if ("SALARY_WEEKLY_RAW".equals(column) || PlayerWorkspaceColumns.MONEY_COLUMNS.contains(column)) {
            return money(value, currency);
        }
        return display(value);
    }

    static String money(Object value, MoneyCurrency currency) {
        Long pounds = sortableLong(value);
        return pounds == null ? "" : MoneyDisplay.format(pounds, currency);
    }

    static String height(PlayerEntity player) {
        Integer cm = player.getHeightCm();
        if (cm == null || cm <= 0) {
            return "";
        }
        int totalInches = (int) Math.round(cm / 2.54);
        return cm + " cm (" + (totalInches / 12) + "'" + (totalInches % 12) + "\")";
    }

    static int compareLongs(Long left, Long right) {
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

    static Long sortableLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
