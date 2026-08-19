package com.github.fmaiassistent.chat;

import com.github.fmaiassistent.service.AssistantChatService;

import java.util.ArrayList;
import java.util.List;

/** Presentation-independent chat history policy. */
public final class ChatHistoryPolicy {
    private ChatHistoryPolicy() {
    }

    public static List<AssistantChatService.ChatTurn> snapshot(
            List<AssistantChatService.ChatTurn> turns) {
        return turns == null ? List.of() : List.copyOf(turns);
    }

    public static List<AssistantChatService.ChatTurn> withoutTrailingUserTurn(
            List<AssistantChatService.ChatTurn> turns) {
        List<AssistantChatService.ChatTurn> snapshot = new ArrayList<>(snapshot(turns));
        if (!snapshot.isEmpty() && snapshot.getLast().user()) {
            snapshot.removeLast();
        }
        return List.copyOf(snapshot);
    }
}
