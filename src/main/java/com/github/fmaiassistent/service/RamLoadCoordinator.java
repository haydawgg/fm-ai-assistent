package com.github.fmaiassistent.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class RamLoadCoordinator {
    private final DatabaseLoadAllService loadAll;
    private final ReentrantLock lock = new ReentrantLock();

    public RamLoadCoordinator(DatabaseLoadAllService loadAll) {
        this.loadAll = loadAll;
    }

    public DatabaseLoadAllService.LoadAllResult loadFromRam() throws IOException {
        if (!lock.tryLock()) {
            throw new IllegalStateException("A RAM load is already in progress");
        }
        try {
            return loadAll.loadAll(null, DatabaseLoadAllService.LoadAllResult.defaultBuild(), null);
        } finally {
            lock.unlock();
        }
    }

    public boolean loading() {
        return lock.isLocked();
    }
}
