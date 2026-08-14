package com.github.fmaiassistent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SchemaLoadTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void loadsRoleCatalogFromCsv() {
        Integer roles = jdbc.queryForObject("select count(*) from fm_role", Integer.class);
        Integer attributes = jdbc.queryForObject("select count(*) from fm_role_attribute", Integer.class);
        assertTrue(roles != null && roles > 10, "expected positional roles from CSV");
        assertTrue(attributes != null && attributes > 50, "expected role attributes from CSV");
    }
}
