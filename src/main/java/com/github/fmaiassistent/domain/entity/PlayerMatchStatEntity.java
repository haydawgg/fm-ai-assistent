package com.github.fmaiassistent.domain.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "player_match_stat", uniqueConstraints = @UniqueConstraint(
        name = "uk_player_match_stat", columnNames = {"player_id", "season_key", "match_date", "competition", "opponent", "stat_name", "source"}))
public class PlayerMatchStatEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, foreignKey = @ForeignKey(name = "fk_match_stat_player"))
    private PlayerEntity player;
    @Column(name = "season_key", nullable = false, length = 32)
    private String seasonKey;
    @Column(name = "match_date", nullable = false, length = 64)
    private String matchDate;
    @Column(nullable = false, length = 256)
    private String competition;
    @Column(nullable = false, length = 256)
    private String opponent;
    @Column(name = "stat_name", nullable = false, length = 128)
    private String statName;
    @Column(name = "stat_value")
    private Double statValue;
    @Column(nullable = false, length = 512)
    private String source;
    @Column(name = "imported_at", nullable = false)
    private OffsetDateTime importedAt;

    protected PlayerMatchStatEntity() {
    }

    public PlayerMatchStatEntity(PlayerEntity player, String seasonKey, String matchDate, String competition,
                                 String opponent, String statName, Double statValue, String source,
                                 OffsetDateTime importedAt) {
        this.player = player;
        this.seasonKey = seasonKey;
        this.matchDate = matchDate;
        this.competition = competition;
        this.opponent = opponent;
        this.statName = statName;
        this.statValue = statValue;
        this.source = source;
        this.importedAt = importedAt;
    }

    public String getSeasonKey() { return seasonKey; }
    public String getMatchDate() { return matchDate; }
    public String getCompetition() { return competition; }
    public String getOpponent() { return opponent; }
    public String getStatName() { return statName; }
    public Double getStatValue() { return statValue; }
    public String getSource() { return source; }
    public OffsetDateTime getImportedAt() { return importedAt; }
}
