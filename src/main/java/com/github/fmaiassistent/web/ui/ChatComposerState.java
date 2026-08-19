package com.github.fmaiassistent.web.ui;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Owns transient composer state so retries and queued sends cannot silently
 * disappear when a stream finishes or a chat session changes.
 */
final class ChatComposerState {
    private final Deque<String> queuedMessages = new ArrayDeque<>();
    private String pendingPrompt = "";
    private String lastUserText = "";
    private String pendingFallbackModel = "";

    void setPendingPrompt(String prompt) {
        pendingPrompt = prompt == null ? "" : prompt;
    }

    String consumePendingPrompt() {
        String prompt = pendingPrompt;
        pendingPrompt = "";
        return prompt;
    }

    boolean hasPendingPrompt() {
        return !pendingPrompt.isBlank();
    }

    void queue(String message) {
        if (message != null && !message.isBlank()) {
            queuedMessages.addLast(message);
        }
    }

    String poll() {
        return queuedMessages.pollFirst();
    }

    int queuedCount() {
        return queuedMessages.size();
    }

    boolean hasQueued() {
        return !queuedMessages.isEmpty();
    }

    void setLastUserText(String text) {
        lastUserText = text == null ? "" : text;
    }

    String lastUserText() {
        return lastUserText;
    }

    void setPendingFallbackModel(String model) {
        pendingFallbackModel = model == null ? "" : model;
    }

    String pendingFallbackModel() {
        return pendingFallbackModel;
    }

    void clearQueued() {
        queuedMessages.clear();
    }

    void resetTransient() {
        clearQueued();
        pendingPrompt = "";
        lastUserText = "";
        pendingFallbackModel = "";
    }
}
