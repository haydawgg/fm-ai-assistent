package com.github.fmaiassistent.service;

import com.github.fmaiassistent.domain.entity.ChatMessageEntity;
import com.github.fmaiassistent.domain.entity.ChatSessionEntity;
import com.github.fmaiassistent.repository.ChatMessageRepository;
import com.github.fmaiassistent.repository.ChatSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatSessionService {
    public static final String DEFAULT_TITLE = "New chat";

    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;

    public ChatSessionService(ChatSessionRepository sessions, ChatMessageRepository messages) {
        this.sessions = sessions;
        this.messages = messages;
    }

    @Transactional
    public ChatSessionEntity create(String model) {
        String id = "openrouter:" + UUID.randomUUID();
        ChatSessionEntity session = new ChatSessionEntity(id, DEFAULT_TITLE, model, OffsetDateTime.now());
        return sessions.save(session);
    }

    @Transactional(readOnly = true)
    public List<ChatSessionEntity> search(String query) {
        List<ChatSessionEntity> all = list();
        if (query == null || query.isBlank()) {
            return all;
        }
        String needle = query.strip().toLowerCase();
        return all.stream()
                .filter(session -> matches(session, needle))
                .toList();
    }

    private boolean matches(ChatSessionEntity session, String needle) {
        if (session.getTitle() != null && session.getTitle().toLowerCase().contains(needle)) {
            return true;
        }
        return messages.findBySessionIdOrderByOrdinalAsc(session.getId()).stream()
                .map(ChatMessageEntity::getBody)
                .anyMatch(body -> body != null && body.toLowerCase().contains(needle));
    }

    @Transactional(readOnly = true)
    public List<ChatSessionEntity> list() {
        return sessions.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<ChatSessionEntity> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return sessions.findById(id);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageEntity> messages(String sessionId) {
        return messages.findBySessionIdOrderByOrdinalAsc(sessionId);
    }

    @Transactional
    public ChatMessageEntity append(String sessionId, String role, String body, String model, MessageExtras extras) {
        ChatSessionEntity session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("chat session not found"));
        int ordinal = messages.maxOrdinalBySessionId(sessionId) + 1;
        ChatMessageEntity row = new ChatMessageEntity(sessionId, ordinal, role, body, model);
        if (extras != null) {
            row.setToolsJson(extras.toolsJson());
            row.setPromptTokens(extras.promptTokens());
            row.setCompletionTokens(extras.completionTokens());
            row.setCostUsd(extras.costUsd());
            row.setTtftMs(extras.ttftMs());
            row.setDurationMs(extras.durationMs());
            row.setReasoning(extras.reasoning());
            row.setGenerationId(extras.generationId());
        }
        messages.save(row);
        if (!session.isTitleLocked() && "user".equals(role) && DEFAULT_TITLE.equals(session.getTitle())) {
            session.setTitle(autoTitle(body));
        }
        session.setModel(model);
        session.setUpdatedAt(OffsetDateTime.now());
        sessions.save(session);
        return row;
    }

    @Transactional
    public void rename(String sessionId, String title) {
        ChatSessionEntity session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("chat session not found"));
        String next = title == null || title.isBlank() ? DEFAULT_TITLE : title.strip();
        session.setTitle(next.length() > 80 ? next.substring(0, 80) : next);
        session.setTitleLocked(true);
        session.setUpdatedAt(OffsetDateTime.now());
        sessions.save(session);
    }

    @Transactional
    public void delete(String sessionId) {
        messages.deleteBySessionId(sessionId);
        sessions.deleteById(sessionId);
    }

    @Transactional
    public void deleteFrom(String sessionId, int ordinal) {
        messages.deleteBySessionIdAndOrdinalGreaterThanEqual(sessionId, ordinal);
        sessions.findById(sessionId).ifPresent(session -> {
            session.setUpdatedAt(OffsetDateTime.now());
            sessions.save(session);
        });
    }

    @Transactional(readOnly = true)
    public double spendUsdSince(OffsetDateTime from) {
        if (from == null) {
            return 0;
        }
        return messages.sumCostUsdSince(from);
    }

    @Transactional
    public String setFeedback(String sessionId, int ordinal, String feedback) {
        ChatMessageEntity row = messages.findBySessionIdAndOrdinal(sessionId, ordinal);
        if (row == null) {
            return null;
        }
        String next = feedback == null || feedback.isBlank() ? null : feedback.strip().toLowerCase();
        if ("up".equals(next) || "down".equals(next)) {
            row.setFeedback(next.equals(row.getFeedback()) ? null : next);
        }
        messages.save(row);
        return row.getFeedback();
    }

    @Transactional
    public ChatMessageEntity updateGeneration(
            String sessionId,
            int ordinal,
            OpenRouterModelCatalog.GenerationLookup lookup,
            String reasoningAppend) {
        ChatMessageEntity row = messages.findBySessionIdAndOrdinal(sessionId, ordinal);
        if (row == null || lookup == null) {
            return row;
        }
        if (lookup.id() != null && !lookup.id().isBlank()) {
            row.setGenerationId(lookup.id().strip());
        }
        if (lookup.promptTokens() != null) {
            row.setPromptTokens(lookup.promptTokens());
        }
        if (lookup.completionTokens() != null) {
            row.setCompletionTokens(lookup.completionTokens());
        }
        if (lookup.totalCost() != null) {
            row.setCostUsd(lookup.totalCost());
        }
        if (reasoningAppend != null && !reasoningAppend.isBlank()) {
            String current = row.getReasoning() == null ? "" : row.getReasoning();
            if (!current.contains(reasoningAppend.strip())) {
                row.setReasoning(current.isBlank() ? reasoningAppend.strip() : current + reasoningAppend);
            }
        }
        return messages.save(row);
    }

    public static String blockIfOverCap(double capUsd, double spentUsd, Double estimateUsd) {
        if (capUsd <= 0) {
            return null;
        }
        double next = spentUsd + (estimateUsd == null ? 0 : estimateUsd);
        if (next <= capUsd) {
            return null;
        }
        return String.format(
                "Daily spend cap $%.2f would be exceeded (already $%.4f today).",
                capUsd,
                spentUsd);
    }

    public static String autoTitle(String firstUserMessage) {
        if (firstUserMessage == null || firstUserMessage.isBlank()) {
            return DEFAULT_TITLE;
        }
        String compact = firstUserMessage.strip().replaceAll("\\s+", " ");
        return compact.length() <= 48 ? compact : compact.substring(0, 45) + "…";
    }

    public record MessageExtras(
            String toolsJson,
            Integer promptTokens,
            Integer completionTokens,
            Double costUsd,
            Integer ttftMs,
            Integer durationMs,
            String reasoning,
            String generationId) {
        public static final MessageExtras NONE = new MessageExtras(null, null, null, null, null, null, null, null);
    }
}
