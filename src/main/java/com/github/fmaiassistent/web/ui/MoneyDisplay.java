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
        MoneyCurrency selected = currency == null ? MoneyCurrency.POUND : currency;
        return BigDecimal.valueOf(pounds)
                .multiply(selected.rateFromPounds())
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    /** Returns the numeric value shown by {@link #format(long, MoneyCurrency)}. */
    public static long displayedAmount(long pounds, MoneyCurrency currency) {
        MoneyCurrency selected = currency == null ? MoneyCurrency.POUND : currency;
        long converted = convert(pounds, selected);
        return selected == MoneyCurrency.POUND ? converted : roundDisplayedAmount(converted);
    }

    /** Converts an amount typed in the display currency back to raw pounds. */
    public static long toBasePounds(long amountInCurrency, MoneyCurrency currency) {
        MoneyCurrency selected = currency == null ? MoneyCurrency.POUND : currency;
        if (selected == MoneyCurrency.POUND || selected.rateFromPounds().compareTo(BigDecimal.ZERO) == 0) {
            return amountInCurrency;
        }
        return BigDecimal.valueOf(amountInCurrency)
                .divide(selected.rateFromPounds(), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    /**
     * A pounds bound that keeps every row the UI shows at or below {@code maxDisplay}. Because
     * {@link #format} rounds converted amounts to display steps (250/1k/25k/1m), a player whose
     * formatted fee equals the typed maximum can have a raw asking price slightly above the naive
     * {@link #toBasePounds} round-trip. This adds the half display step and rounds the division up,
     * then filters with {@code asking_price <= bound} so such boundary players stay included.
     */
    public static long inclusiveMaxToBasePounds(long maxDisplayCurrency, MoneyCurrency currency) {
        MoneyCurrency selected = currency == null ? MoneyCurrency.POUND : currency;
        if (selected == MoneyCurrency.POUND || selected.rateFromPounds().compareTo(BigDecimal.ZERO) == 0) {
            return maxDisplayCurrency;
        }
        long inclusive = maxDisplayCurrency + displayStep(maxDisplayCurrency) / 2;
        return BigDecimal.valueOf(inclusive)
                .divide(selected.rateFromPounds(), 0, RoundingMode.CEILING)
                .longValue();
    }

    private static long displayStep(long amount) {
        long abs = Math.abs(amount);
        if (abs < 25_000L) {
            return 250L;
        }
        if (abs < 100_000L) {
            return 1_000L;
        }
        if (abs < 1_000_000L) {
            return 25_000L;
        }
        return 1_000_000L;
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
        return selected.symbol() + NumberFormat.getIntegerInstance(Locale.US)
                .format(displayedAmount(pounds, selected));
    }
}
