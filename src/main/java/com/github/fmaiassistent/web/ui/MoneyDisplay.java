package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.enums.MoneyCurrency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Formats raw pound amounts in the user-selected display currency, exactly like the scouting desk.
 * Single source for currency conversion so every surface (scouting desk, moneyball) shows one currency.
 */
public final class MoneyDisplay {

    private MoneyDisplay() {
    }

    /** Converts raw pounds to the display currency. */
    public static long convert(long pounds, MoneyCurrency currency) {
        return BigDecimal.valueOf(pounds)
                .multiply(currency.rateFromPounds())
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    /** Rounds a converted amount to a display-friendly step (250 / 1k / 25k / 1m ...). */
    public static long roundDisplayedAmount(long amount) {
        long abs = Math.abs(amount);
        long step;
        if (abs < 25_000L) {
            step = 250L;
        } else if (abs < 100_000L) {
            step = 1_000L;
        } else if (abs < 1_000_000L) {
            step = 25_000L;
        } else {
            step = 1_000_000L;
        }
        return amount == 0 || step <= 0 ? amount : Math.round(amount / (double) step) * step;
    }

    /** Formats raw pounds in the display currency, e.g. "$6,000,000". */
    public static String format(long pounds, MoneyCurrency currency) {
        MoneyCurrency selected = currency == null ? MoneyCurrency.POUND : currency;
        long converted = convert(pounds, selected);
        if (selected != MoneyCurrency.POUND) {
            converted = roundDisplayedAmount(converted);
        }
        return selected.symbol() + NumberFormat.getIntegerInstance(Locale.US).format(converted);
    }
}
