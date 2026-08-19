package com.github.fmaiassistent.web.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatComposerStateTest {
    @Test
    void preservesQueueOrderAndRetryContext() {
        ChatComposerState state = new ChatComposerState();
        state.queue("first");
        state.queue("second");
        state.setLastUserText("retry me");
        state.setPendingFallbackModel("fallback");

        assertEquals("first", state.poll());
        assertEquals("second", state.poll());
        assertEquals("retry me", state.lastUserText());
        assertEquals("fallback", state.pendingFallbackModel());
        assertFalse(state.hasQueued());
    }

    @Test
    void resetClearsPendingPromptAndTransientSendState() {
        ChatComposerState state = new ChatComposerState();
        state.setPendingPrompt("open the squad");
        state.queue("queued");
        state.setLastUserText("last");
        state.setPendingFallbackModel("model");

        state.resetTransient();

        assertFalse(state.hasPendingPrompt());
        assertFalse(state.hasQueued());
        assertEquals("", state.lastUserText());
        assertEquals("", state.pendingFallbackModel());
    }
}
