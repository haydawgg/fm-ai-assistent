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
        assertEquals(AssistantChatService.MAX_HISTORY_MESSAGES + 2, messages.size());
        assertEquals("latest", messages.getLast().getText());
        assertTrue(messages.getFirst().getText().contains("Earlier conversation"));
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
    void observingToolCallbackCapturesInputAndOutput() {
        List<AssistantChatService.ToolTrace> traces = new ArrayList<>();
        ToolCallback delegate = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("fm26_status").description("status").inputSchema("{}").build();
            }

            @Override
            public String call(String toolInput) {
                return "{\"ok\":true}";
            }
        };
        ToolCallback[] wrapped = AssistantChatService.observing(
                new ToolCallback[] {delegate}, ignored -> { }, traces::add);
        assertEquals("{\"ok\":true}", wrapped[0].call("{\"probe\":1}"));
        assertEquals(1, traces.size());
        assertEquals("fm26_status", traces.getFirst().name());
        assertEquals("{\"probe\":1}", traces.getFirst().input());
        assertEquals("{\"ok\":true}", traces.getFirst().output());
    }

    @Test
    void omittedCountIgnoresMessagesWithinTheCap() {
        assertEquals(0, AssistantChatService.omittedCount(List.of(
                new AssistantChatService.ChatTurn(true, "a"),
                new AssistantChatService.ChatTurn(false, "b"))));
        List<AssistantChatService.ChatTurn> history = new ArrayList<>();
        for (int index = 0; index < AssistantChatService.MAX_HISTORY_MESSAGES + 3; index++) {
            history.add(new AssistantChatService.ChatTurn(true, "m" + index));
        }
        assertEquals(3, AssistantChatService.omittedCount(history));
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
        assertEquals(0.7, options.getTemperature());
        assertEquals(Boolean.TRUE, options.getExtraBody().get("include_reasoning"));
        assertEquals("medium", ((java.util.Map<?, ?>) options.getExtraBody().get("reasoning")).get("effort"));
        assertEquals(null, options.getTopP());
    }

    @Test
    void chatOptionsIncludeTopPWhenSet() {
        var options = AssistantChatService.chatOptions("sk-or-test", "openai/gpt-4.1-mini", ChatTone.CONCISE, 0.4);
        assertEquals(0.4, options.getTopP());
        assertEquals(0.2, options.getTemperature());
    }

    @Test
    void transientErrorsInclude429() {
        assertTrue(AssistantChatService.transientError(new RuntimeException("OpenRouter HTTP 429 rate limit")));
        assertTrue(!AssistantChatService.transientError(new RuntimeException("bad request")));
    }

    @Test
    void compactSummaryKeepsUserGoals() {
        String summary = AssistantChatService.compactSummary(List.of(
                new AssistantChatService.ChatTurn(true, "Find a cheap DM"),
                new AssistantChatService.ChatTurn(false, "Here is one")));
        assertTrue(summary.contains("cheap DM"));
    }

    @Test
    void systemPromptIncludesInstructionsContextAndStaleSnapshot() {
        String prompt = AssistantChatService.systemPrompt(new AssistantChatService.ChatGrounding(
                "Ajax",
                "Euro (€)",
                "Moneyball",
                "DM, fee cap 40m",
                "2035-06-01",
                false,
                true,
                "Answer in Dutch."));
        assertTrue(prompt.contains("Ajax"));
        assertTrue(prompt.contains("Euro"));
        assertTrue(prompt.contains("Moneyball"));
        assertTrue(prompt.contains("DM, fee cap 40m"));
        assertTrue(prompt.contains("2035-06-01"));
        assertTrue(prompt.contains("stale"));
        assertTrue(prompt.contains("Answer in Dutch."));
        assertTrue(prompt.contains("raw pounds"));
    }

    @Test
    void extractReasoningReadsNestedDetails() {
        String text = AssistantChatService.extractReasoning(java.util.Map.of(
                "reasoning_details",
                List.of(java.util.Map.of("type", "reasoning.text", "text", "Need a cheap DM"))));
        assertEquals("Need a cheap DM", text);
    }

    @Test
    void thinkTagsAreSplitFromTheAnswer() {
        AssistantChatService.ThinkSplitter.Piece piece = AssistantChatService.ThinkSplitter.splitComplete(
                "<think>plan the XI</think>\nHere is the side.");
        assertEquals("plan the XI", piece.reasoning());
        assertTrue(piece.answer().contains("Here is the side."));
        assertTrue(!piece.answer().contains("<think>"));
    }

    @Test
    void thinkSplitterHandlesChunkedTags() {
        AssistantChatService.ThinkSplitter splitter = new AssistantChatService.ThinkSplitter();
        AssistantChatService.ThinkSplitter.Piece a = splitter.push("<th");
        AssistantChatService.ThinkSplitter.Piece b = splitter.push("ink>secret</th");
        AssistantChatService.ThinkSplitter.Piece c = splitter.push("ink>visible");
        assertEquals("", a.answer() + a.reasoning());
        assertEquals("", b.answer() + b.reasoning());
        assertEquals("secret", c.reasoning());
        assertEquals("visible", c.answer());
    }

    @Test
    void thinkingTagsAreSplitFromTheAnswer() {
        AssistantChatService.ThinkSplitter.Piece piece = AssistantChatService.ThinkSplitter.splitComplete(
                "<thinking>plan the XI</thinking>\nHere is the side.");
        assertEquals("plan the XI", piece.reasoning());
        assertTrue(piece.answer().contains("Here is the side."));
        assertTrue(!piece.answer().contains("<thinking>"));
    }

    @Test
    void reasoningSuffixEmitsOnlyNewText() {
        assertEquals("Need a cheap DM", AssistantChatService.reasoningSuffix("", "Need a cheap DM"));
        assertEquals(" for Ajax", AssistantChatService.reasoningSuffix("Need a cheap DM", "Need a cheap DM for Ajax"));
        assertEquals("", AssistantChatService.reasoningSuffix("Need a cheap DM", "Need a cheap DM"));
    }

    @Test
    void extractGenerationIdPrefersGenPrefix() {
        assertEquals("gen-abc12345", AssistantChatService.extractGenerationId(
                java.util.Map.of("id", "gen-abc12345", "model", "openai/gpt-4.1-mini")));
    }
}
