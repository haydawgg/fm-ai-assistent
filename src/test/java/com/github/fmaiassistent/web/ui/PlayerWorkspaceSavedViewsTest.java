package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.repository.PlayerFilterCriteria;
import com.github.fmaiassistent.service.AppSettingsService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerWorkspaceSavedViewsTest {
    @Test
    void namesAndLookupAreCaseInsensitiveAndBlankLookupIsEmpty() {
        AppSettingsService settings = mock(AppSettingsService.class);
        when(settings.playerViews()).thenReturn(
                List.of(new SavedPlayerView("Desk", PlayerFilterCriteria.empty(), false)));

        PlayerWorkspaceSavedViews views = new PlayerWorkspaceSavedViews(settings);

        assertEquals(List.of("Desk"), views.names());
        assertTrue(views.find("desk").isPresent());
        assertTrue(views.find(" ").isEmpty());
    }
}
