package com.github.fmaiassistent.service;

import com.github.fmaiassistent.domain.entity.ClubEntity;
import com.github.fmaiassistent.exporter.ClubExporter;
import com.github.fmaiassistent.repository.ClubRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class PlayerJdbcInsertTest {

    @Autowired
    private PlayerDatabaseService players;

    @Autowired
    private ClubDatabaseService clubDatabaseService;

    @Autowired
    private ClubRepository clubs;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Transactional
    void savePlayerChunkInsertsViaJdbc() {
        ClubEntity club = clubs.saveAndFlush(ClubEntity.fromExportRow(Map.of(
                "sourceAddress", 99L,
                "name", "Test FC",
                "gender", "male",
                "competition", "League",
                "reputation", 5000,
                "nation", "England",
                "balance", 1L,
                "transferBudget", 1L,
                "payrollBudget", 1L)));

        Map<String, Object> row = new HashMap<>();
        row.put("name", "Hero");
        row.put("ca", 140);
        row.put("pa", 150);
        row.put("index", 7);
        row.put("record", "0xabc");
        row.put("club", "Test FC");
        row.put("playing_club", "Test FC");
        row.put("Striker", 18);
        row.put("traits", "Places Shots");
        row.put("_club_address", 99L);
        row.put("_playing_club_address", 99L);

        players.savePlayerChunk(List.of(row), Map.of(99L, club));

        Integer count = jdbc.queryForObject("select count(*) from players where name = 'Hero'", Integer.class);
        assertEquals(1, count);
        Long clubId = jdbc.queryForObject("select club_id from players where name = 'Hero'", Long.class);
        assertEquals(club.getId(), clubId);
        Integer striker = jdbc.queryForObject("select striker from players where name = 'Hero'", Integer.class);
        assertEquals(18, striker);
        String traits = jdbc.queryForObject("select traits from players where name = 'Hero'", String.class);
        assertEquals("Places Shots", traits);
        Long generatedId = jdbc.queryForObject("select id from players where name = 'Hero'", Long.class);
        assertNotNull(generatedId);
    }

    @Test
    @Transactional
    void saveExportedClubsInsertsViaJdbc() {
        clubDatabaseService.saveExported(new ClubExporter.ExportResult(List.of(Map.of(
                "sourceAddress", 42L,
                "name", "Jdbc FC",
                "gender", "male",
                "competition", "League",
                "reputation", 4000,
                "nation", "England",
                "balance", 2L,
                "transferBudget", 3L,
                "payrollBudget", 4L))));

        Integer count = jdbc.queryForObject("select count(*) from clubs where name = 'Jdbc FC'", Integer.class);
        assertEquals(1, count);
        Long id = jdbc.queryForObject("select id from clubs where name = 'Jdbc FC'", Long.class);
        assertNotNull(id);
        Long source = jdbc.queryForObject("select source_address from clubs where name = 'Jdbc FC'", Long.class);
        assertEquals(42L, source);
    }
}
