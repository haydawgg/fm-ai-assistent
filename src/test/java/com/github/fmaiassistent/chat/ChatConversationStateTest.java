package com.github.fmaiassistent.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatConversationStateTest {
    @Test
    void snapshotIsImmutableAndTrailingUserCanBeRemoved() {
        ChatConversationState state = new ChatConversationState();
        state.addUser("hello");

        List<?> snapshot = state.historySnapshot();
        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        assertTrue(state.historyWithoutTrailingUser().isEmpty());
    }

    @Test
    void clearConversationResetsIdentityTranscriptAndReplacementState() {
        ChatConversationState state = new ChatConversationState();
        state.selectConversation("conversation-1");
        state.addUser("hello");
        state.setLastUserOrdinal(7);
        state.setPendingReplaceFrom(7);

        state.clearConversation();

        assertFalse(state.hasConversation());
        assertEquals(-1, state.lastUserOrdinal());
        assertEquals(null, state.pendingReplaceFrom());
        assertTrue(state.historyIsEmpty());
    }
}
