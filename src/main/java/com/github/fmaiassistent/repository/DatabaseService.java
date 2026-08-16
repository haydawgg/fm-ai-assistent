package com.github.fmaiassistent.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DatabaseService {

    private final List<String> TABLES_TO_CLEAR = List.of(
            "PLAYERS",
            "CLUBS",
            "COMPETITIONS",
            "LOAD_METADATA"
    );
    private final JdbcTemplate jdbcTemplate;

    public DatabaseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void clearAllTables() {
        for (String table : TABLES_TO_CLEAR) {
            jdbcTemplate.execute("DELETE FROM " + quote(table));
        }
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
