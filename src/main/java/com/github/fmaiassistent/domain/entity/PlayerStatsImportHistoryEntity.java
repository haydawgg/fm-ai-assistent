package com.github.fmaiassistent.domain.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "player_stats_import_history")
public class PlayerStatsImportHistoryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "imported_at", nullable = false)
    private OffsetDateTime importedAt;
    @Column(nullable = false, length = 512)
    private String source;
    @Column(name = "season_key", nullable = false, length = 32)
    private String seasonKey;
    @Column(name = "stats_scope", nullable = false, length = 64)
    private String statsScope;
    @Column(nullable = false)
    private int rows;
    @Column(name = "matched_rows", nullable = false)
    private int matchedRows;
    @Column(name = "invalid_rows", nullable = false)
    private int invalidRows;
    @Column(name = "unmatched_rows", nullable = false)
    private int unmatchedRows;
    @Column(name = "imported_stat_rows", nullable = false)
    private int importedStatRows;
    @Column(name = "match_stat_rows", nullable = false)
    private int matchStatRows;
    @Column(nullable = false, length = 32)
    private String status;

    protected PlayerStatsImportHistoryEntity() {
    }

    public PlayerStatsImportHistoryEntity(OffsetDateTime importedAt, String source, String seasonKey,
                                          String statsScope, int rows, int matchedRows, int invalidRows,
                                          int unmatchedRows, int importedStatRows, int matchStatRows, String status) {
        this.importedAt = importedAt;
        this.source = source;
        this.seasonKey = seasonKey;
        this.statsScope = statsScope;
        this.rows = rows;
        this.matchedRows = matchedRows;
        this.invalidRows = invalidRows;
        this.unmatchedRows = unmatchedRows;
        this.importedStatRows = importedStatRows;
        this.matchStatRows = matchStatRows;
        this.status = status;
    }

    public Long getId() { return id; }
    public OffsetDateTime getImportedAt() { return importedAt; }
    public String getSource() { return source; }
    public String getSeasonKey() { return seasonKey; }
    public String getStatsScope() { return statsScope; }
    public int getRows() { return rows; }
    public int getMatchedRows() { return matchedRows; }
    public int getInvalidRows() { return invalidRows; }
    public int getUnmatchedRows() { return unmatchedRows; }
    public int getImportedStatRows() { return importedStatRows; }
    public int getMatchStatRows() { return matchStatRows; }
    public String getStatus() { return status; }
}
