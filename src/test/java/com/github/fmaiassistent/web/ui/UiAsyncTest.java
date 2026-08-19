package com.github.fmaiassistent.web.ui;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiAsyncTest {
    @Test
    void preservesSuccessfulBackgroundResult() {
        CompletableFuture<String> task = UiAsync.submit(
                null,
                () -> "ready",
                ignored -> {
                },
                ignored -> {
                });

        assertEquals("ready", task.join());
    }

    @Test
    void preservesBackgroundFailureForTheOwningView() {
        CompletableFuture<String> task = UiAsync.submit(
                null,
                () -> {
                    throw new IllegalStateException("load failed");
                },
                ignored -> {
                },
                ignored -> {
                });

        CompletionException failure = assertThrows(CompletionException.class, task::join);
        assertEquals("load failed", failure.getCause().getMessage());
    }
}
