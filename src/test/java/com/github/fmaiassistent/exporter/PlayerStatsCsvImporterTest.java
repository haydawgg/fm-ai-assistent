package com.github.fmaiassistent.exporter;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStatsCsvImporterTest {
    @Test
    void parsesFmSemicolonCsvPreservesZeroAndRejectsImpossibleRelationships() throws Exception {
        PlayerStatsCsvImporter.ImportBatch batch = PlayerStatsCsvImporter.parse(new StringReader(
                "Player;Club;Apps;Starts;Minutes;Gls;Ast;Rating;xG\n"
                        + "A Player;Club;0;0;0;0;0;0;0\n"
                        + "B Player;Club;10;12;5;8;9;11;1.4\n"), "2025/26", "all_competitions");

        assertEquals(2, batch.rows().size());
        PlayerStatsCsvImporter.Row first = batch.rows().getFirst();
        assertEquals(0, first.appearances());
        assertEquals(0.0, first.averageRating());
        assertEquals(0.0, first.extras().get("xg"));
        PlayerStatsCsvImporter.Row second = batch.rows().get(1);
        assertNull(second.starts());
        assertNull(second.goals());
        assertNull(second.assists());
        assertNull(second.averageRating());
    }

    @Test
    void parsesQuotedCommaCsvAndMatchContext() throws Exception {
        PlayerStatsCsvImporter.ImportBatch batch = PlayerStatsCsvImporter.parse(new StringReader(
                "Player,Club,Date,Competition,Opponent,Goals,Shots\n"
                        + "\"A, Player\",Club,2026-08-20,League,Other,1,4\n"), "2025/26", "all_competitions");

        assertEquals("A, Player", batch.rows().getFirst().name());
        assertTrue(batch.rows().getFirst().hasMatchContext());
        assertEquals(4.0, batch.rows().getFirst().extras().get("shots"));
    }

    @Test
    void parsesEscapedQuotesTabsAndDecimalComma() throws Exception {
        PlayerStatsCsvImporter.ImportBatch batch = PlayerStatsCsvImporter.parse(new StringReader(
                "Player\tRating\tKey passes\n\"A \"\"Quoted\"\"\"\t7,25\t3\n"), "2025/26", "all_competitions");

        assertEquals("A \"Quoted\"", batch.rows().getFirst().name());
        assertEquals(7.25, batch.rows().getFirst().averageRating());
        assertEquals(3.0, batch.rows().getFirst().extras().get("key_passes"));
    }

    @Test
    void canonicalizesRichMetricsAndDerivesPer90OnlyWhenMinutesAreKnown() throws Exception {
        PlayerStatsCsvImporter.ImportBatch batch = PlayerStatsCsvImporter.parse(new StringReader(
                "Player,Apps,Starts,Minutes,Goals,Assists,Expected Goals,Expected Assists,Key Passes\n"
                        + "A Player,10,6,900,9,3,8.4,2.1,25\n"
                        + "B Player,2,0,0,0,0,0,0,0\n"), "2025/26", "all_competitions");

        var first = batch.rows().getFirst().extras();
        assertEquals(8.4, first.get("xg"));
        assertEquals(2.1, first.get("xa"));
        assertEquals(25.0, first.get("key_passes"));
        assertEquals(0.9, first.get("goals_per_90"));
        assertEquals(0.3, first.get("assists_per_90"));
        assertEquals(90.0, first.get("minutes_per_appearance"));
        assertEquals(60.0, first.get("starts_percentage"));
        assertTrue(batch.rows().get(1).extras().getOrDefault("goals_per_90", null) == null);
    }
}
