package com.github.fmaiassistent.copilot;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CopilotConversationServiceTest {

    @Test
    void failTurnMarksInProgressAssistantCompleted() {
        String turnId = "t1";
        List<CopilotConversationItem> items = new ArrayList<>();
        items.add(new CopilotConversationItem("assistant-" + turnId,
                CopilotConversationItem.Kind.ASSISTANT, "partial", "inProgress", null));

        CopilotConversationService.recordTurnFailure(items, turnId, "boom");

        assertEquals("completed", items.getFirst().status());
        assertEquals(CopilotConversationItem.Kind.SYSTEM, items.getLast().kind());
        assertEquals("failed", items.getLast().status());
        assertEquals("boom", items.getLast().text());
    }
}
