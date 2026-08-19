package com.github.fmaiassistent.service;

import com.github.fmaiassistent.domain.entity.LoadMetadataEntity;
import com.github.fmaiassistent.repository.DatabaseService;
import com.github.fmaiassistent.repository.LoadMetadataRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/** H2 implementation of the transaction-scoped snapshot publication seam. */
@Service
public class SnapshotPublicationCoordinator implements SnapshotPublicationPort {
    private final DatabaseService database;
    private final LoadMetadataRepository metadata;

    public SnapshotPublicationCoordinator(DatabaseService database, LoadMetadataRepository metadata) {
        this.database = database;
        this.metadata = metadata;
    }

    @Override
    public void stage(String snapshotId) {
        requireSnapshotId(snapshotId);
        database.clearAllTables();
        metadata.save(new LoadMetadataEntity("snapshot_state", "staging"));
        metadata.save(new LoadMetadataEntity("snapshot_id", snapshotId));
        metadata.save(new LoadMetadataEntity("snapshot_started_at", OffsetDateTime.now().toString()));
    }

    @Override
    public void publish(String snapshotId) {
        requireSnapshotId(snapshotId);
        metadata.save(new LoadMetadataEntity("snapshot_id", snapshotId));
        metadata.save(new LoadMetadataEntity("snapshot_state", "published"));
        metadata.save(new LoadMetadataEntity("snapshot_published_at", OffsetDateTime.now().toString()));
    }

    private static void requireSnapshotId(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new IllegalArgumentException("Snapshot id is required");
        }
    }
}
