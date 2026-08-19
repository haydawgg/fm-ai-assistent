package com.github.fmaiassistent.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatContentLimitsTest {
    @Test
    void acceptsNullAndBoundaryValues() {
        assertDoesNotThrow(() -> ChatContentLimits.requireMessage(null));
        assertEquals("ok", ChatContentLimits.requireToolsJson("ok"));
        assertDoesNotThrow(() -> ChatContentLimits.requireReasoning("x".repeat(ChatContentLimits.MAX_REASONING_CHARACTERS)));
    }

    @Test
    void rejectsOversizedContentWithTheFieldName() {
        IllegalArgumentException message = assertThrows(
                IllegalArgumentException.class,
                () -> ChatContentLimits.requireMessage("x".repeat(ChatContentLimits.MAX_MESSAGE_CHARACTERS + 1)));
        assertEquals("message exceeds the maximum size of 100000 characters", message.getMessage());

        IllegalArgumentException trace = assertThrows(
                IllegalArgumentException.class,
                () -> ChatContentLimits.requireToolsJson("x".repeat(ChatContentLimits.MAX_TOOLS_JSON_CHARACTERS + 1)));
        assertEquals("tool trace exceeds the maximum size of 100000 characters", trace.getMessage());
    }

    @Test
    void validatesMessageExtrasAsOnePersistenceBoundary() {
        ChatSessionService.MessageExtras safe = new ChatSessionService.MessageExtras(
                "{}", 1, 2, 0.01, 3, 4, "thinking", "generation").validated();
        assertEquals("{}", safe.toolsJson());
        assertEquals("thinking", safe.reasoning());

        assertThrows(IllegalArgumentException.class, () -> new ChatSessionService.MessageExtras(
                "x".repeat(ChatContentLimits.MAX_TOOLS_JSON_CHARACTERS + 1),
                null, null, null, null, null, null, null).validated());
    }
}
