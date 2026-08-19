package com.github.fmaiassistent.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.time.OffsetDateTime;

@Entity
@Table(name = "chat_message")
public class ChatMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", length = 64, nullable = false)
    private String sessionId;

    @Column
    private int ordinal;

    @Column(length = 32)
    private String role;

    @Lob
    @Column(name = "body", length = 100000)
    private String body;

    @Column(length = 256)
    private String model;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Lob
    @Column(name = "tools_json", length = 100000)
    private String toolsJson;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "cost_usd", precision = 20, scale = 8)
    private BigDecimal costUsd;

    @Column(name = "ttft_ms")
    private Integer ttftMs;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(length = 8)
    private String feedback;

    @Lob
    @Column(name = "reasoning", length = 100000)
    private String reasoning;

    @Column(name = "generation_id", length = 128)
    private String generationId;

    protected ChatMessageEntity() {
    }

    public ChatMessageEntity(String sessionId, int ordinal, String role, String body, String model) {
        this.sessionId = sessionId;
        this.ordinal = ordinal;
        this.role = role;
        this.body = body;
        this.model = model;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public String getRole() {
        return role;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getModel() {
        return model;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getToolsJson() {
        return toolsJson;
    }

    public void setToolsJson(String toolsJson) {
        this.toolsJson = toolsJson;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Double getCostUsd() {
        return costUsd == null ? null : costUsd.doubleValue();
    }

    public BigDecimal getCostUsdDecimal() {
        return costUsd;
    }

    public void setCostUsd(Double costUsd) {
        if (costUsd == null || !Double.isFinite(costUsd)) {
            this.costUsd = null;
            return;
        }
        this.costUsd = BigDecimal.valueOf(costUsd)
                .setScale(8, RoundingMode.HALF_UP);
    }

    public Integer getTtftMs() {
        return ttftMs;
    }

    public void setTtftMs(Integer ttftMs) {
        this.ttftMs = ttftMs;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public String getGenerationId() {
        return generationId;
    }

    public void setGenerationId(String generationId) {
        this.generationId = generationId;
    }
}
