package com.github.fmaiassistent.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "chat_session")
public class ChatSessionEntity {
    @Id
    @Column(length = 64)
    private String id;

    @Column(length = 256)
    private String title;

    @Column(length = 256)
    private String model;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "title_locked")
    private boolean titleLocked;

    protected ChatSessionEntity() {
    }

    public ChatSessionEntity(String id, String title, String model, OffsetDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.model = model;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isTitleLocked() {
        return titleLocked;
    }

    public void setTitleLocked(boolean titleLocked) {
        this.titleLocked = titleLocked;
    }
}
