package com.github.fmaiassistent.domain.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "player_imported_stat", uniqueConstraints = @UniqueConstraint(
        name = "uk_player_imported_stat", columnNames = {"player_id", "season_key", "stats_scope", "stat_name", "source"}))
public class PlayerImportedStatEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, foreignKey = @ForeignKey(name = "fk_imported_stat_player"))
    private PlayerEntity player;
    @Column(name = "season_key", nullable = false, length = 32)
    private String seasonKey;
    @Column(name = "stats_scope", nullable = false, length = 64)
    private String statsScope;
    @Column(name = "stat_name", nullable = false, length = 128)
    private String statName;
    @Column(name = "stat_value")
    private Double statValue;
    @Column(nullable = false, length = 512)
    private String source;
    @Column(name = "imported_at", nullable = false)
    private OffsetDateTime importedAt;

    protected PlayerImportedStatEntity() {
    }

    public PlayerImportedStatEntity(PlayerEntity player, String seasonKey, String statsScope, String statName,
                                    Double statValue, String source, OffsetDateTime importedAt) {
        this.player = player;
        this.seasonKey = seasonKey;
        this.statsScope = statsScope;
        this.statName = statName;
        this.statValue = statValue;
        this.source = source;
        this.importedAt = importedAt;
    }

    public String getSeasonKey() { return seasonKey; }
    public String getStatsScope() { return statsScope; }
    public String getStatName() { return statName; }
    public Double getStatValue() { return statValue; }
    public String getSource() { return source; }
    public OffsetDateTime getImportedAt() { return importedAt; }
}
