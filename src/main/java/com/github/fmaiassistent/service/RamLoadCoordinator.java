package com.github.fmaiassistent.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Service
public class RamLoadCoordinator {
    private final DatabaseLoadAllService loadAll;
    private final ReentrantLock lock = new ReentrantLock();

    public RamLoadCoordinator(DatabaseLoadAllService loadAll) {
        this.loadAll = loadAll;
    }

    public DatabaseLoadAllService.LoadAllResult loadFromRam() throws IOException {
        return loadFromRam(LoadProgressReporter.NONE);
    }

    public DatabaseLoadAllService.LoadAllResult loadFromRam(Consumer<LoadProgress> progress) throws IOException {
        if (!lock.tryLock()) {
            throw new IllegalStateException("A RAM load is already in progress");
        }
        try {
            return loadAll.loadAll(null, DatabaseLoadAllService.LoadAllResult.defaultBuild(), null, progress);
        } finally {
            lock.unlock();
        }
    }

    public boolean loading() {
        return lock.isLocked();
    }
}
