package com.github.fmaiassistent.mcp;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.player.AttributeDefinitions;
import com.github.fmaiassistent.player.FieldDef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a market model from the loaded player data and rates transfer deals against it.
 *
 * <p>The market for a player is the median asking price and weekly wage of comparable
 * players: same best position, CA band (10), age band (5) and PA band (20). When a bucket
 * holds too few samples the lookup falls back to coarser keys (without PA, then without age),
 * mirroring how a used-car site compares a listing with similar cars instead of one exact car.
 *
 * <p>A deal compares the total cost of ownership (fee + 3 years of wages) with the market
 * equivalent. {@code deal_score = marketCost / totalCost}, so 1.0 is an average-priced deal
 * and higher is better: excellent when the cost is below 60% of market, good 60-80%,
 * average 80-120%, overpriced above 120%.
 */
public final class MarketValuation {

    /** Minimum samples before a bucket is used as market evidence. */
    public static final int MIN_BUCKET_SAMPLES = 5;
    /** Contract length used to fold wages into the total cost of a deal. */
    public static final long CONTRACT_YEARS = 3;
    public static final long WEEKS_PER_YEAR = 52;

    /** deal_score thresholds; the inverse of the 0.6 / 0.8 / 1.2 cost-vs-market ratios. */
    public static final double EXCELLENT_DEAL_MIN = 1.0 / 0.6;
    public static final double GOOD_DEAL_MIN = 1.0 / 0.8;
    public static final double AVERAGE_DEAL_MIN = 1.0 / 1.2;

    /** How strongly deal value moves the signing rating: 1.0 score = neutral, 2.0 score = 1.5x. */
    public static final double VALUE_FACTOR_SLOPE = 0.5;
    public static final double VALUE_FACTOR_MIN = 0.3;
    public static final double VALUE_FACTOR_MAX = 1.5;

    /** Median fee (price) and weekly wage of a comparables bucket, with sample count. */
    public record Market(long price, long wage, int samples) {
    }

    /** The rated deal: score vs market, tier, and both costs. */
    public record Deal(double score, String tier, Market market, long totalCost, long marketCost) {
    }

    private record Bucket(int position, int caBand, int ageBand, int paBand) {
        private Bucket withoutPa() {
            return new Bucket(position, caBand, ageBand, -1);
        }

        private Bucket withoutAge() {
            return new Bucket(position, caBand, -1, -1);
        }
    }

    private static final class PriceList {
        private final List<Long> prices = new ArrayList<>();
        private final List<Long> wages = new ArrayList<>();

        private void add(PlayerEntity player) {
            prices.add(value(player.getAskingPrice()));
            wages.add((long) value(player.getSalaryWeeklyRaw()));
        }
    }

    private final Map<Bucket, Market> byPositionCaAgePa;
    private final Map<Bucket, Market> byPositionCaAge;
    private final Map<Bucket, Market> byPositionCa;
    private final int pricedPlayers;

    private MarketValuation(
            Map<Bucket, Market> byPositionCaAgePa,
            Map<Bucket, Market> byPositionCaAge,
            Map<Bucket, Market> byPositionCa,
            int pricedPlayers) {
        this.byPositionCaAgePa = byPositionCaAgePa;
        this.byPositionCaAge = byPositionCaAge;
        this.byPositionCa = byPositionCa;
        this.pricedPlayers = pricedPlayers;
    }

    /**
     * Builds the market model in a single pass over the loaded players. Only players with a
     * known asking price and a valid position/CA/age participate in the market.
     */
    public static MarketValuation build(List<PlayerEntity> players) {
        Map<Bucket, PriceList> level1 = new HashMap<>();
        Map<Bucket, PriceList> level2 = new HashMap<>();
        Map<Bucket, PriceList> level3 = new HashMap<>();
        int priced = 0;
        for (PlayerEntity player : players) {
            if (value(player.getAskingPrice()) <= 0) {
                continue;
            }
            priced++;
            int position = bestPositionIndex(player);
            Integer ca = player.getCa();
            Integer age = asInteger(player.getAge());
            if (position < 0 || ca == null || ca <= 0 || age == null || age < 0) {
                continue;
            }
            Bucket key = new Bucket(position, ca / 10, age / 5, value(player.getPa()) / 20);
            level1.computeIfAbsent(key, ignored -> new PriceList()).add(player);
            level2.computeIfAbsent(key.withoutPa(), ignored -> new PriceList()).add(player);
            level3.computeIfAbsent(key.withoutAge(), ignored -> new PriceList()).add(player);
        }
        return new MarketValuation(median(level1), median(level2), median(level3), priced);
    }

    /** Number of players with a known asking price that shaped the model. */
    public int pricedPlayers() {
        return pricedPlayers;
    }

    /** Number of distinct finest-grained buckets in the model. */
    public int bucketCount() {
        return byPositionCaAgePa.size();
    }

    /**
     * Market for a player, falling back to coarser comparables until the bucket has enough
     * samples, or {@code null} when the player cannot be placed in any market.
     */
    public Market marketFor(PlayerEntity player) {
        Integer ca = player.getCa();
        Integer age = asInteger(player.getAge());
        int position = bestPositionIndex(player);
        if (position < 0 || ca == null || ca <= 0 || age == null || age < 0) {
            return null;
        }
        Bucket key = new Bucket(position, ca / 10, age / 5, value(player.getPa()) / 20);
        Market market = byPositionCaAgePa.get(key);
        if (market == null || market.samples() < MIN_BUCKET_SAMPLES) {
            market = byPositionCaAge.get(key.withoutPa());
            if (market == null || market.samples() < MIN_BUCKET_SAMPLES) {
                market = byPositionCa.get(key.withoutAge());
            }
        }
        return market != null && market.samples() >= MIN_BUCKET_SAMPLES ? market : null;
    }

    /**
     * Rates a deal for a player: {@code fee} is the transfer fee (0 for free agents) and the
     * total cost folds in {@value #CONTRACT_YEARS} years of wages. Returns {@code null} when
     * the player has no comparable market to rate against.
     */
    public Deal deal(PlayerEntity player, long fee) {
        Market market = marketFor(player);
        if (market == null) {
            return null;
        }
        long weeklyWage = value(player.getSalaryWeeklyRaw());
        long totalCost = fee + CONTRACT_YEARS * WEEKS_PER_YEAR * weeklyWage;
        long marketCost = market.price() + CONTRACT_YEARS * WEEKS_PER_YEAR * market.wage();
        double score = totalCost <= 0 ? 9.99 : Math.min(9.99, round2(marketCost / (double) totalCost));
        return new Deal(score, tier(score), market, totalCost, marketCost);
    }

    /** Used-car style label for a deal score. */
    public static String tier(double dealScore) {
        if (dealScore >= EXCELLENT_DEAL_MIN) {
            return "excellent";
        }
        if (dealScore >= GOOD_DEAL_MIN) {
            return "good";
        }
        if (dealScore >= AVERAGE_DEAL_MIN) {
            return "average";
        }
        return "overpriced";
    }

    /**
     * 0-100 signing rating: player quality (0-1, typically CA/PA blended and age-adjusted)
     * multiplied by a value factor derived from the deal. At market price the rating equals
     * the quality percentage; below-market deals are boosted (capped at 1.5x), above-market
     * deals are penalised. Always capped at 100.
     */
    public static int signingRating(double quality, Deal deal) {
        double valueFactor = clamp(1.0 + (deal.score() - 1.0) * VALUE_FACTOR_SLOPE, VALUE_FACTOR_MIN, VALUE_FACTOR_MAX);
        return (int) Math.round(Math.min(100.0, quality * 100.0 * valueFactor));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Index of the player's best position field, or -1 when every position is 0. */
    public static int bestPositionIndex(PlayerEntity player) {
        List<FieldDef> fields = AttributeDefinitions.POSITION_FIELDS;
        int bestIndex = -1;
        int bestScore = 0;
        for (int index = 0; index < fields.size(); index++) {
            Object value = player.getColumnValue(FmAiAssistentTools.columnName(fields.get(index)));
            int score = value instanceof Number number ? number.intValue() : 0;
            if (score > bestScore) {
                bestScore = score;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private static Map<Bucket, Market> median(Map<Bucket, PriceList> raw) {
        Map<Bucket, Market> out = new HashMap<>();
        for (Map.Entry<Bucket, PriceList> entry : raw.entrySet()) {
            PriceList list = entry.getValue();
            out.put(entry.getKey(), new Market(median(list.prices), median(list.wages), list.prices.size()));
        }
        return out;
    }

    private static long median(List<Long> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        return sorted.get(sorted.size() / 2);
    }

    private static Integer asInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
