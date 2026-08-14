package com.github.fmaiassistent.service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class LoadProgressReporter {
    public static final Consumer<LoadProgress> NONE = progress -> {
    };

    private static final long SLOT_INTERVAL = 2_000;
    private static final long NANOS_INTERVAL = 100_000_000L;

    private final Consumer<LoadProgress> listener;
    private final AtomicLong lastDone = new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong lastNanos = new AtomicLong(0);

    public LoadProgressReporter(Consumer<LoadProgress> listener) {
        this.listener = listener == null ? NONE : listener;
    }

    public static Consumer<LoadProgress> orNone(Consumer<LoadProgress> listener) {
        return listener == null ? NONE : listener;
    }

    public void start(LoadProgress.Phase phase, long total) {
        report(new LoadProgress(phase, 0, total, 0), true);
    }

    public void report(LoadProgress progress) {
        report(progress, false);
    }

    public void finish(LoadProgress progress) {
        report(progress, true);
    }

    public void report(LoadProgress progress, boolean force) {
        if (listener == NONE) {
            return;
        }
        long now = System.nanoTime();
        long previousDone = lastDone.get();
        long previousNanos = lastNanos.get();
        if (!force
                && previousDone != Long.MIN_VALUE
                && progress.done() - previousDone < SLOT_INTERVAL
                && now - previousNanos < NANOS_INTERVAL) {
            return;
        }
        lastDone.set(progress.done());
        lastNanos.set(now);
        listener.accept(progress);
    }
}
