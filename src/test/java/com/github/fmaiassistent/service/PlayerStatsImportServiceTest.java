package com.github.fmaiassistent.service;

import com.github.fmaiassistent.domain.entity.LoadMetadataEntity;
import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.repository.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlayerStatsImportServiceTest {
    @Test
    void blankCsvValuesDoNotEraseExistingTrustedValues() throws Exception {
        PlayerEntity player = PlayerEntity.fromExportRow(Map.of(
                "name", "A Player", "club", "Club", "appearances", 8, "goals", 3));
        PlayerRepository players = mock(PlayerRepository.class);
        when(players.findByNameContainingIgnoreCase("A Player")).thenReturn(List.of(player));
        LoadMetadataRepository metadata = mock(LoadMetadataRepository.class);
        when(metadata.findById("season_key")).thenReturn(Optional.of(new LoadMetadataEntity("season_key", "2025/26")));
        PlayerStatsImportService service = new PlayerStatsImportService(players,
                mock(PlayerImportedStatRepository.class), mock(PlayerMatchStatRepository.class), metadata,
                mock(PlayerStatsImportHistoryRepository.class));

        service.importCsv(new ByteArrayInputStream("Player;Club;Apps;Gls\nA Player;Club;;\n".getBytes()), "stats.csv");

        assertEquals(8, player.getAppearances());
        assertEquals(3, player.getGoals());
        verify(players).saveAll(any());
    }

    @Test
    void previewSeparatesAmbiguousUnmatchedAndInvalidRows() throws Exception {
        PlayerEntity first = PlayerEntity.fromExportRow(Map.of("name", "Same Name", "club", "One"));
        PlayerEntity second = PlayerEntity.fromExportRow(Map.of("name", "Same Name", "club", "Two"));
        PlayerRepository players = mock(PlayerRepository.class);
        when(players.findByNameContainingIgnoreCase(any())).thenReturn(List.of(first, second));
        LoadMetadataRepository metadata = mock(LoadMetadataRepository.class);
        when(metadata.findById("season_key")).thenReturn(Optional.of(new LoadMetadataEntity("season_key", "2025/26")));
        PlayerStatsImportService service = new PlayerStatsImportService(players,
                mock(PlayerImportedStatRepository.class), mock(PlayerMatchStatRepository.class), metadata,
                mock(PlayerStatsImportHistoryRepository.class));

        PlayerStatsImportService.ImportPreview preview = service.preview((
                "Player;Club;Minutes;Goals\nSame Name;;10;1\nMissing;Club;bad;0\n").getBytes(), "stats.csv");

        assertEquals(PlayerStatsImportService.MatchStatus.AMBIGUOUS, preview.rows().get(0).status());
        assertEquals(PlayerStatsImportService.MatchStatus.INVALID, preview.rows().get(1).status());
    }
}
