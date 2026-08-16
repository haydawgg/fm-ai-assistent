package com.github.fmaiassistent.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Service
public class RamLoadCoordinator {
    private final DatabaseLoadAllService loadAll;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicLong jobSequence = new AtomicLong();
    private final AtomicReference<LoadJob> currentJob = new AtomicReference<>();
    private final ExecutorService asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public RamLoadCoordinator(DatabaseLoadAllService loadAll) {
        this.loadAll = loadAll;
    }

    public DatabaseLoadAllService.LoadAllResult loadFromRam() throws IOException {
        return loadFromRam(LoadProgressReporter.NONE);
    }

    public DatabaseLoadAllService.LoadAllResult loadFromRam(Consumer<LoadProgress> progress) throws IOException {
        if (!loading.compareAndSet(false, true)) {
            throw new LoadInProgressException("A RAM load is already in progress");
        }
        if (!lock.tryLock()) {
            loading.set(false);
            throw new LoadInProgressException("A RAM load is already in progress");
        }
        try {
            LoadJob job = new LoadJob(nextJobId());
            currentJob.set(job);
            Consumer<LoadProgress> reporter = progress == null ? ignored -> { } : progress;
            DatabaseLoadAllService.LoadAllResult result = loadAll.loadAll(
                    null,
                    DatabaseLoadAllService.LoadAllResult.defaultBuild(),
                    null,
                    update -> {
                        job.progress.set(update);
                        reporter.accept(update);
                    });
            job.complete(result);
            return result;
        } catch (IOException | RuntimeException ex) {
            LoadJob job = currentJob.get();
            if (job != null) {
                job.fail(ex);
            }
            throw ex;
        } finally {
            loading.set(false);
            lock.unlock();
        }
    }

    public String startLoad() {
        if (!loading.compareAndSet(false, true)) {
            throw new LoadInProgressException("A RAM load is already in progress");
        }
        String jobId = nextJobId();
        LoadJob job = new LoadJob(jobId);
        currentJob.set(job);
        try {
            asyncExecutor.submit(() -> {
                try {
                    DatabaseLoadAllService.LoadAllResult result = loadAll.loadAll(
                            null,
                            DatabaseLoadAllService.LoadAllResult.defaultBuild(),
                            null,
                            progress -> job.progress.set(progress));
                    job.complete(result);
                } catch (IOException | RuntimeException ex) {
                    job.fail(ex);
                } finally {
                    loading.set(false);
                }
            });
        } catch (RuntimeException ex) {
            loading.set(false);
            throw ex;
        }
        return jobId;
    }

    public LoadStatus status() {
        LoadJob job = currentJob.get();
        if (job == null) {
            return new LoadStatus(null, "idle", null, null, null);
        }
        return job.status();
    }

    private String nextJobId() {
        return "ram-" + jobSequence.incrementAndGet();
    }

    public boolean loading() {
        return loading.get();
    }

    @jakarta.annotation.PreDestroy
    void shutdown() {
        asyncExecutor.shutdownNow();
    }

    public record LoadStatus(
            String jobId,
            String state,
            LoadProgress progress,
            DatabaseLoadAllService.LoadAllResult result,
            String error) {
    }

    private static final class LoadJob {
        private final String id;
        private final AtomicReference<LoadProgress> progress = new AtomicReference<>();
        private volatile String state = "running";
        private volatile DatabaseLoadAllService.LoadAllResult result;
        private volatile String error;

        private LoadJob(String id) {
            this.id = id;
        }

        private void complete(DatabaseLoadAllService.LoadAllResult result) {
            this.result = result;
            this.state = "completed";
        }

        private void fail(Throwable throwable) {
            this.error = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
            this.state = "failed";
        }

        private LoadStatus status() {
            return new LoadStatus(id, state, progress.get(), result, error);
        }
    }

    public static class LoadInProgressException extends RuntimeException {
        public LoadInProgressException(String message) {
            super(message);
        }
    }
}
