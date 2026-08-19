package com.github.fmaiassistent.chat;

import com.github.fmaiassistent.service.AssistantChatService;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the mutable state of the selected chat conversation.
 *
 * <p>This is deliberately independent of Vaadin components and persistence
 * entities. The view can orchestrate those boundaries while this module keeps
 * conversation identity, transcript state, and replacement bookkeeping
 * consistent.</p>
 */
public final class ChatConversationState {
    private final List<AssistantChatService.ChatTurn> history = new ArrayList<>();
    private String conversationId;
    private int lastUserOrdinal = -1;
    private Integer pendingReplaceFrom;

    public String conversationId() {
        return conversationId;
    }

    public boolean hasConversation() {
        return conversationId != null;
    }

    public void selectConversation(String id) {
        conversationId = id;
    }

    public int lastUserOrdinal() {
        return lastUserOrdinal;
    }

    public void setLastUserOrdinal(int ordinal) {
        lastUserOrdinal = ordinal;
    }

    public Integer pendingReplaceFrom() {
        return pendingReplaceFrom;
    }

    public void setPendingReplaceFrom(Integer ordinal) {
        pendingReplaceFrom = ordinal;
    }

    public List<AssistantChatService.ChatTurn> historySnapshot() {
        return ChatHistoryPolicy.snapshot(history);
    }

    public List<AssistantChatService.ChatTurn> historyWithoutTrailingUser() {
        return ChatHistoryPolicy.withoutTrailingUserTurn(history);
    }

    public boolean historyIsEmpty() {
        return history.isEmpty();
    }

    public int historySize() {
        return history.size();
    }

    public AssistantChatService.ChatTurn historyAt(int index) {
        return history.get(index);
    }

    public void addUser(String text) {
        history.add(new AssistantChatService.ChatTurn(true, text));
    }

    public void addAssistant(String text) {
        history.add(new AssistantChatService.ChatTurn(false, text));
    }

    public void clearHistory() {
        history.clear();
    }

    public void clearConversation() {
        conversationId = null;
        history.clear();
        lastUserOrdinal = -1;
        pendingReplaceFrom = null;
    }
}
