package com.github.fmaiassistent.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantChatServiceTest {

    @Test
    void secondTurnIncludesTheFirstUserMessage() {
        List<AssistantChatService.ChatTurn> history = List.of(
                new AssistantChatService.ChatTurn(true, "Who is my best striker?"),
                new AssistantChatService.ChatTurn(false, "Ada"));
        List<Message> messages = AssistantChatService.promptMessages(history, "Why him?");

        assertEquals(3, messages.size());
        assertInstanceOf(UserMessage.class, messages.get(0));
        assertEquals("Who is my best striker?", messages.get(0).getText());
        assertInstanceOf(AssistantMessage.class, messages.get(1));
        assertEquals("Ada", messages.get(1).getText());
        assertInstanceOf(UserMessage.class, messages.get(2));
        assertEquals("Why him?", messages.get(2).getText());
    }

    @Test
    void historyIsCapped() {
        List<AssistantChatService.ChatTurn> history = new ArrayList<>();
        for (int index = 0; index < AssistantChatService.MAX_HISTORY_MESSAGES + 4; index++) {
            history.add(new AssistantChatService.ChatTurn(index % 2 == 0, "m" + index));
        }
        List<Message> messages = AssistantChatService.promptMessages(history, "latest");
        assertEquals(AssistantChatService.MAX_HISTORY_MESSAGES + 1, messages.size());
        assertEquals("latest", messages.getLast().getText());
        assertTrue(messages.getFirst().getText().startsWith("m"));
    }

    @Test
    void observingToolCallbackReportsTheToolNameBeforeTheDelegateRuns() {
        List<String> seen = new ArrayList<>();
        ToolCallback delegate = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("fm26_status").description("status").inputSchema("{}").build();
            }

            @Override
            public String call(String toolInput) {
                seen.add("ran:" + toolInput);
                return "{}";
            }
        };
        ToolCallback[] wrapped = AssistantChatService.observing(new ToolCallback[] {delegate}, seen::add);
        assertEquals("{}", wrapped[0].call("ping"));
        assertEquals(List.of("Checking snapshot", "ran:ping"), seen);
    }

    @Test
    void toolLabelsAreHuman() {
        assertEquals("Searching shortlist", AssistantChatService.labelForTool("fm26_transfer_shortlist"));
        assertEquals("Working", AssistantChatService.labelForTool(""));
    }

    @Test
    void chatOptionsTargetOpenRouter() {
        var options = AssistantChatService.chatOptions("sk-or-test", "openai/gpt-4.1-mini");
        assertEquals("https://openrouter.ai/api/v1", options.getBaseUrl());
        assertEquals("sk-or-test", options.getApiKey());
        assertEquals("openai/gpt-4.1-mini", options.getModel());
    }
}
