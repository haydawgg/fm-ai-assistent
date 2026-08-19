package com.github.fmaiassistent.service;

/**
 * Transaction-scoped publication seam for imported football snapshots.
 *
 * <p>{@link #stage(String)} must leave the previous committed snapshot
 * readable until the surrounding transaction commits. {@link #publish(String)}
 * is only called after every staged row has been written. If the transaction
 * rolls back, both operations are rolled back and the previous snapshot
 * remains authoritative.</p>
 */
public interface SnapshotPublicationPort {
    void stage(String snapshotId);

    void publish(String snapshotId);
}
