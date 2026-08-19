package com.github.fmaiassistent.chat;

import com.github.fmaiassistent.service.AssistantChatService;

/** Domain event emitted while a chat provider produces a reply. */
public record ChatStreamEvent(
        Kind kind,
        String text,
        AssistantChatService.ToolTrace trace,
        AssistantChatService.UsageSnapshot usage,
        String generationId) {
    public ChatStreamEvent(Kind kind, String text) {
        this(kind, text, null, null, null);
    }

    public ChatStreamEvent(
            Kind kind,
            String text,
            AssistantChatService.ToolTrace trace,
            AssistantChatService.UsageSnapshot usage) {
        this(kind, text, trace, usage, null);
    }

    public enum Kind {
        TOKEN,
        TOOL,
        TOOL_TRACE,
        USAGE,
        REASONING
    }
}
