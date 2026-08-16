package com.github.fmaiassistent.service;

import com.github.fmaiassistent.domain.entity.ChatMessageEntity;
import com.github.fmaiassistent.domain.entity.ChatSessionEntity;
import com.github.fmaiassistent.repository.DatabaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ChatSessionPersistTest {
    @Autowired
    private ChatSessionService sessions;
    @Autowired
    private DatabaseService databaseService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void appendAutoTitlesThenRenameLocks() {
        ChatSessionEntity session = sessions.create("openai/gpt-4.1-mini");
        sessions.append(session.getId(), "user", "Build my best XI from the live formation please",
                "openai/gpt-4.1-mini", ChatSessionService.MessageExtras.NONE);
        ChatSessionEntity titled = sessions.find(session.getId()).orElseThrow();
        assertEquals("Build my best XI from the live formation please", titled.getTitle());

        sessions.rename(session.getId(), "Matchday XI");
        sessions.append(session.getId(), "assistant", "Here is the XI.", "openai/gpt-4.1-mini",
                ChatSessionService.MessageExtras.NONE);
        assertEquals("Matchday XI", sessions.find(session.getId()).orElseThrow().getTitle());

        sessions.deleteFrom(session.getId(), 1);
        List<ChatMessageEntity> remaining = sessions.messages(session.getId());
        assertEquals(1, remaining.size());
        assertEquals("user", remaining.getFirst().getRole());
        sessions.delete(session.getId());
    }

    @Test
    void ramTruncateLeavesChatTables() {
        ChatSessionEntity session = sessions.create("test-model");
        sessions.append(session.getId(), "user", "keep me", "test-model", ChatSessionService.MessageExtras.NONE);
        databaseService.clearAllTables();
        Integer sessionsLeft = jdbc.queryForObject("select count(*) from chat_session where id = ?", Integer.class, session.getId());
        Integer messagesLeft = jdbc.queryForObject("select count(*) from chat_message where session_id = ?", Integer.class, session.getId());
        assertEquals(1, sessionsLeft);
        assertEquals(1, messagesLeft);
        sessions.delete(session.getId());
    }

    @Test
    void blockIfOverCapUsesEstimate() {
        assertEquals(null, ChatSessionService.blockIfOverCap(0, 1, 2.0));
        assertEquals(null, ChatSessionService.blockIfOverCap(1, 0.2, 0.3));
        assertTrue(ChatSessionService.blockIfOverCap(1, 0.9, 0.2).contains("Daily spend cap"));
    }

    @Test
    void autoTitleTruncatesLongFirstMessage() {
        String title = ChatSessionService.autoTitle("a".repeat(80));
        assertTrue(title.length() <= 48);
        assertTrue(title.endsWith("…"));
    }

    @Test
    void feedbackTogglesOnSameVote() {
        ChatSessionEntity session = sessions.create("openai/gpt-4.1-mini");
        sessions.append(session.getId(), "assistant", "Here is the XI.", "openai/gpt-4.1-mini",
                ChatSessionService.MessageExtras.NONE);
        assertEquals("up", sessions.setFeedback(session.getId(), 0, "up"));
        assertEquals(null, sessions.setFeedback(session.getId(), 0, "up"));
        assertEquals("down", sessions.setFeedback(session.getId(), 0, "down"));
        sessions.delete(session.getId());
    }

    @Test
    void reasoningIsPersistedOnAssistantTurns() {
        ChatSessionEntity session = sessions.create("openai/gpt-4.1-mini");
        sessions.append(session.getId(), "assistant", "Here is the XI.", "openai/gpt-4.1-mini",
                new ChatSessionService.MessageExtras(null, 10, 20, 0.01, 100, 400, "checking formation", "gen-test"));
        assertEquals("checking formation", sessions.messages(session.getId()).getFirst().getReasoning());
        assertEquals("gen-test", sessions.messages(session.getId()).getFirst().getGenerationId());
        sessions.delete(session.getId());
    }

    @Test
    void appendUsesNextOrdinalAfterAGap() {
        ChatSessionEntity session = sessions.create("openai/gpt-4.1-mini");
        sessions.append(session.getId(), "user", "first", "openai/gpt-4.1-mini", ChatSessionService.MessageExtras.NONE);
        jdbc.update("insert into chat_message (session_id, ordinal, role, body) values (?, ?, ?, ?)",
                session.getId(), 5, "assistant", "gapped");
        sessions.append(session.getId(), "user", "after gap", "openai/gpt-4.1-mini", ChatSessionService.MessageExtras.NONE);
        List<ChatMessageEntity> rows = sessions.messages(session.getId());
        assertEquals(6, rows.getLast().getOrdinal());
        sessions.delete(session.getId());
    }
}
