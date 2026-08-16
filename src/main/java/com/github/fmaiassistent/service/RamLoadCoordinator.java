package com.github.fmaiassistent.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Service
public class RamLoadCoordinator {
    private final DatabaseLoadAllService loadAll;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final AtomicBoolean loading = new AtomicBoolean(false);

    public RamLoadCoordinator(DatabaseLoadAllService loadAll) {
        this.loadAll = loadAll;
    }

    public DatabaseLoadAllService.LoadAllResult loadFromRam() throws IOException {
        return loadFromRam(LoadProgressReporter.NONE);
    }

    public DatabaseLoadAllService.LoadAllResult loadFromRam(Consumer<LoadProgress> progress) throws IOException {
        if (!lock.tryLock()) {
            throw new LoadInProgressException("A RAM load is already in progress");
        }
        try {
            loading.set(true);
            return loadAll.loadAll(null, DatabaseLoadAllService.LoadAllResult.defaultBuild(), null, progress);
        } finally {
            loading.set(false);
            lock.unlock();
        }
    }

    public boolean loading() {
        return loading.get();
    }

    public static class LoadInProgressException extends RuntimeException {
        public LoadInProgressException(String message) {
            super(message);
        }
    }
}
