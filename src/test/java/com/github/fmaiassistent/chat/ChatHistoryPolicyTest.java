package com.github.fmaiassistent.chat;

import com.github.fmaiassistent.service.AssistantChatService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatHistoryPolicyTest {
    @Test
    void snapshotsHistoryAndRemovesOnlyTrailingUserTurn() {
        List<AssistantChatService.ChatTurn> turns = new ArrayList<>(List.of(
                new AssistantChatService.ChatTurn(true, "question"),
                new AssistantChatService.ChatTurn(false, "answer"),
                new AssistantChatService.ChatTurn(true, "retry")));

        assertEquals(3, ChatHistoryPolicy.snapshot(turns).size());
        assertEquals(2, ChatHistoryPolicy.withoutTrailingUserTurn(turns).size());
        assertEquals("answer", ChatHistoryPolicy.withoutTrailingUserTurn(turns).getLast().text());
    }
}
