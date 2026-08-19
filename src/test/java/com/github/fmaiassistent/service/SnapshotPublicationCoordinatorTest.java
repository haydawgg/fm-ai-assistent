package com.github.fmaiassistent.service;

import com.github.fmaiassistent.repository.DatabaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class SnapshotPublicationCoordinatorTest {
    @Autowired
    private SnapshotPublicationPort publication;
    @Autowired
    private DatabaseService database;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanSnapshotTables() {
        database.clearAllTables();
    }

    @Test
    void failedStagingRollsBackAndKeepsPreviousSnapshot() {
        jdbc.update("insert into competitions (name) values (?)", "Previous League");
        jdbc.update("insert into load_metadata (meta_key, meta_value) values (?, ?)",
                "snapshot_state", "published");

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            publication.stage("replacement");
            throw new IllegalStateException("simulated import failure");
        }));

        assertEquals(1, jdbc.queryForObject(
                "select count(*) from competitions where name = ?", Integer.class, "Previous League"));
        assertEquals("published", metadata("snapshot_state"));
    }

    @Test
    void publicationMarkerAppearsOnlyWhenTheTransactionCommits() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            publication.stage("committed-snapshot");
            publication.publish("committed-snapshot");
        });

        assertEquals("published", metadata("snapshot_state"));
        assertEquals("committed-snapshot", metadata("snapshot_id"));
    }

    private String metadata(String key) {
        return jdbc.queryForObject(
                "select meta_value from load_metadata where meta_key = ?", String.class, key);
    }
}
