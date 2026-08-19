package com.github.fmaiassistent.web.ui;

import com.vaadin.flow.component.UI;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared lifecycle for cancellable background work owned by a Vaadin view.
 *
 * <p>Work is cancelled when the owning UI detaches. Completion and failure
 * callbacks are marshalled back to the UI thread, and detached UIs are
 * ignored. Callers keep responsibility for their loading and empty states;
 * this module centralizes the concurrency and error-delivery seam.</p>
 */
final class UiAsync {
    private UiAsync() {
    }

    static <T> CompletableFuture<T> submit(
            UI ui,
            Supplier<T> work,
            Consumer<T> success,
            Consumer<Throwable> failure) {
        CompletableFuture<T> task = CompletableFuture.supplyAsync(work);
        if (ui != null) {
            ui.addDetachListener(event -> task.cancel(true));
        }
        task.thenAccept(result -> OpenRouterModelPicker.access(ui, () -> success.accept(result)))
                .exceptionally(error -> {
                    if (!task.isCancelled()) {
                        OpenRouterModelPicker.access(ui, () -> failure.accept(error));
                    }
                    return null;
                });
        return task;
    }

    static <T> CompletableFuture<T> observe(
            UI ui,
            CompletableFuture<T> task,
            Consumer<T> success,
            Consumer<Throwable> failure) {
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }
        if (ui != null) {
            ui.addDetachListener(event -> task.cancel(true));
        }
        task.thenAccept(result -> OpenRouterModelPicker.access(ui, () -> success.accept(result)))
                .exceptionally(error -> {
                    if (!task.isCancelled()) {
                        OpenRouterModelPicker.access(ui, () -> failure.accept(error));
                    }
                    return null;
                });
        return task;
    }

    static void access(UI ui, Runnable action) {
        OpenRouterModelPicker.access(ui, action::run);
    }
}
