package com.github.fmaiassistent.mcp;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketValuationTest {

    @Test
    void ratesDealAgainstMarketMedian() {
        List<PlayerEntity> players = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            players.add(player("p" + index, 8_000_000L, 15_000, "Market FC"));
        }
        for (int index = 0; index < 10; index++) {
            players.add(player("p" + (10 + index), 10_000_000L, 20_000, "Market FC"));
        }
        for (int index = 0; index < 10; index++) {
            players.add(player("p" + (20 + index), 12_000_000L, 25_000, "Market FC"));
        }
        MarketValuation market = MarketValuation.build(players);

        // Median price 10m, median wage 20k in the bucket.
        MarketValuation.Market bucket = market.marketFor(player("target", 6_000_000L, 15_000, "Market FC"));
        assertNotNull(bucket);
        assertEquals(10_000_000L, bucket.price());
        assertEquals(20_000L, bucket.wage());
        assertEquals(30, bucket.samples());

        // Fee 6m + 3 years of 15k wages = 8.34m vs market 13.12m -> good deal.
        MarketValuation.Deal deal = market.deal(player("target", 6_000_000L, 15_000, "Market FC"), 6_000_000L);
        assertNotNull(deal);
        assertEquals(1.57, deal.score(), 0.001);
        assertEquals("good", deal.tier());
        assertEquals(8_340_000L, deal.totalCost());
        assertEquals(13_120_000L, deal.marketCost());
    }

    @Test
    void labelsFreeAgentAsExcellent() {
        MarketValuation market = MarketValuation.build(marketPlayers());
        MarketValuation.Deal deal = market.deal(player("free agent", 0L, 18_000, ""), 0L);

        assertNotNull(deal);
        assertEquals(2_808_000L, deal.totalCost());
        assertEquals("excellent", deal.tier());
        assertTrue(deal.score() > MarketValuation.EXCELLENT_DEAL_MIN);
    }

    @Test
    void labelsOverpricedDeal() {
        MarketValuation market = MarketValuation.build(marketPlayers());
        MarketValuation.Deal deal = market.deal(player("expensive", 13_000_000L, 25_000, "Market FC"), 13_000_000L);

        assertNotNull(deal);
        assertEquals(16_900_000L, deal.totalCost());
        assertEquals("overpriced", deal.tier());
    }

    @Test
    void unknownWeeklyWageUsesMarketWageInDealCost() {
        MarketValuation market = MarketValuation.build(marketPlayers());
        Map<String, Object> row = new HashMap<>();
        row.put("name", "unknown-wage");
        row.put("ca", 135);
        row.put("pa", 150);
        row.put("age", 22);
        row.put("asking_price", 6_000_000L);
        row.put("club", "Market FC");
        row.put("Striker", 16);
        MarketValuation.Deal deal = market.deal(PlayerEntity.fromExportRow(row), 6_000_000L);
        assertNotNull(deal);
        assertEquals(6_000_000L + MarketValuation.CONTRACT_YEARS * MarketValuation.WEEKS_PER_YEAR * deal.market().wage(),
                deal.totalCost());
    }

    @Test
    void fallsBackToCoarserBucketWhenFineGrainedBucketIsThin() {
        List<PlayerEntity> players = new ArrayList<>();
        // Same position/CA/age, split across two PA bands: 2 + 3 samples (< 5 each).
        for (int index = 0; index < 2; index++) {
            players.add(player("low-pa-" + index, 10_000_000L, 20_000, "Market FC", 148));
        }
        for (int index = 0; index < 3; index++) {
            players.add(player("high-pa-" + index, 10_000_000L, 20_000, "Market FC", 172));
        }
        MarketValuation market = MarketValuation.build(players);

        MarketValuation.Market marketFor = market.marketFor(player("target", 6_000_000L, 15_000, "Market FC", 150));
        assertNotNull(marketFor);
        assertEquals(5, marketFor.samples());
    }

    @Test
    void noMarketWhenNoComparablePlayers() {
        List<PlayerEntity> players = List.of(
                player("gk", 10_000_000L, 20_000, "Market FC", "Goalkeeper", 18),
                player("cb", 10_000_000L, 20_000, "Market FC", "DefenderCentral", 18),
                player("cm", 10_000_000L, 20_000, "Market FC", "MidfielderCentral", 18),
                player("st", 10_000_000L, 20_000, "Market FC", "Striker", 18));
        MarketValuation market = MarketValuation.build(players);

        assertNull(market.marketFor(player("target", 6_000_000L, 15_000, "Market FC")));
        assertNull(market.deal(player("target", 6_000_000L, 15_000, "Market FC"), 6_000_000L));
    }

    @Test
    void unpricedPlayersDoNotShapeTheMarket() {
        List<PlayerEntity> players = marketPlayers();
        players.add(player("unpriced", 0L, 5_000, "Market FC"));
        MarketValuation market = MarketValuation.build(players);

        assertEquals(30, market.pricedPlayers());
        MarketValuation.Market bucket = market.marketFor(player("target", 6_000_000L, 15_000, "Market FC"));
        assertNotNull(bucket);
        assertEquals(30, bucket.samples());
        assertEquals(10_000_000L, bucket.price());
    }

    @Test
    void pricedPlayersCountsOnlyPlayersThatEnterBuckets() {
        List<PlayerEntity> players = marketPlayers();
        Map<String, Object> incomplete = new HashMap<>();
        incomplete.put("name", "no-ca");
        incomplete.put("asking_price", 8_000_000L);
        incomplete.put("salary_weekly_raw", 15_000);
        incomplete.put("age", 22);
        incomplete.put("club", "Market FC");
        incomplete.put("Striker", 16);
        players.add(PlayerEntity.fromExportRow(incomplete));

        MarketValuation market = MarketValuation.build(players);
        assertEquals(30, market.pricedPlayers());
    }

    @Test
    void unknownWagesAreOmittedFromWageMedian() {
        List<PlayerEntity> players = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            players.add(player("paid-" + index, 10_000_000L, 20_000, "Market FC"));
        }
        for (int index = 0; index < 6; index++) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", "unknown-wage-" + index);
            row.put("ca", 135);
            row.put("pa", 150);
            row.put("age", 22);
            row.put("asking_price", 10_000_000L);
            row.put("club", "Market FC");
            row.put("Striker", 16);
            players.add(PlayerEntity.fromExportRow(row));
        }
        MarketValuation market = MarketValuation.build(players);
        MarketValuation.Market bucket = market.marketFor(player("target", 10_000_000L, 20_000, "Market FC"));
        assertNotNull(bucket);
        assertEquals(20_000L, bucket.wage());
    }

    @Test
    void tierBoundaries() {
        assertEquals("excellent", MarketValuation.tier(MarketValuation.EXCELLENT_DEAL_MIN));
        assertEquals("good", MarketValuation.tier(MarketValuation.GOOD_DEAL_MIN));
        assertEquals("average", MarketValuation.tier(MarketValuation.AVERAGE_DEAL_MIN));
        assertEquals("excellent", MarketValuation.tier(2.0));
        assertEquals("good", MarketValuation.tier(1.4));
        assertEquals("average", MarketValuation.tier(1.0));
        assertEquals("overpriced", MarketValuation.tier(0.5));
    }

    @Test
    void signingRatingBlendsQualityAndValue() {
        MarketValuation market = MarketValuation.build(marketPlayers());

        // At market price: rating equals the quality percentage.
        MarketValuation.Deal atMarket = market.deal(player("at-market", 10_000_000L, 20_000, "Market FC"), 10_000_000L);
        assertNotNull(atMarket);
        assertEquals(1.0, atMarket.score(), 0.001);
        assertEquals(75, MarketValuation.signingRating(0.75, atMarket));
        assertEquals(90, MarketValuation.signingRating(0.9, atMarket));

        // Below market: boosted, capped at 100.
        MarketValuation.Deal cheap = market.deal(player("cheap", 5_000_000L, 15_000, "Market FC"), 5_000_000L);
        assertNotNull(cheap);
        assertTrue(cheap.score() > 1.0);
        assertEquals(100, MarketValuation.signingRating(0.75, cheap));

        // Free agent: maximum value boost.
        MarketValuation.Deal free = market.deal(player("free", 0L, 15_000, ""), 0L);
        assertNotNull(free);
        assertEquals(100, MarketValuation.signingRating(0.75, free));

        // Above market: penalised.
        MarketValuation.Deal expensive = market.deal(player("expensive", 20_000_000L, 40_000, "Market FC"), 20_000_000L);
        assertNotNull(expensive);
        assertTrue(expensive.score() < 1.0);
        assertEquals(56, MarketValuation.signingRating(0.75, expensive));
    }

    /** 30 priced strikers: median price 10m, median wage 20k. */
    private static List<PlayerEntity> marketPlayers() {
        List<PlayerEntity> players = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            players.add(player("p" + index, 8_000_000L, 15_000, "Market FC"));
        }
        for (int index = 0; index < 10; index++) {
            players.add(player("p" + (10 + index), 10_000_000L, 20_000, "Market FC"));
        }
        for (int index = 0; index < 10; index++) {
            players.add(player("p" + (20 + index), 12_000_000L, 25_000, "Market FC"));
        }
        return players;
    }

    @Test
    void excludesStaffFromMarketModel() {
        List<PlayerEntity> players = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            players.add(player("keeper" + index, 5_000_000L, 20_000, "Market FC", "Goalkeeper", 16));
        }
        // Staff-like entry: has an asking price, but no position attributes.
        players.add(PlayerEntity.fromExportRow(Map.<String, Object>of(
                "name", "Coach",
                "ca", 150,
                "pa", 70,
                "age", 45,
                "club", "World",
                "asking_price", 1_000L,
                "salary_weekly_raw", 0)));

        MarketValuation market = MarketValuation.build(players);
        assertEquals(6, market.pricedPlayers());

        MarketValuation.Market keeperMarket = market.marketFor(
                player("probe", 5_000_000L, 20_000, "Market FC", "Goalkeeper", 16));
        assertNotNull(keeperMarket);
        assertEquals(5_000_000L, keeperMarket.price());
        assertEquals(20_000L, keeperMarket.wage());
        assertEquals(6, keeperMarket.samples());
    }

    private static PlayerEntity player(String name, long price, int wage, String club) {
        return player(name, price, wage, club, 150);
    }

    private static PlayerEntity player(String name, long price, int wage, String club, int pa) {
        return player(name, price, wage, club, "Striker", 16, pa);
    }

    private static PlayerEntity player(String name, long price, int wage, String club, String position, int positionScore) {
        return player(name, price, wage, club, position, positionScore, 150);
    }

    private static PlayerEntity player(String name, long price, int wage, String club, String position, int positionScore, int pa) {
        return PlayerEntity.fromExportRow(Map.<String, Object>of(
                "name", name,
                "ca", 135,
                "pa", pa,
                "age", 22,
                "asking_price", price,
                "salary_weekly_raw", wage,
                "club", club,
                position, positionScore));
    }
}
