package com.github.fmaiassistent.web.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveSelectedXiPanelTest {

    @Test
    void parseReadsPositionAndNameLines() {
        List<LiveSelectedXiPanel.SelectedSlot> slots = LiveSelectedXiPanel.parse("GK,Wellenreuther\nDL,Smal\n");
        assertEquals(2, slots.size());
        assertEquals("GK", slots.get(0).position());
        assertEquals("Wellenreuther", slots.get(0).playerName());
        assertEquals("DL", slots.get(1).position());
        assertEquals("Smal", slots.get(1).playerName());
    }

    @Test
    void parseTreatsBlankAndNullAsEmpty() {
        assertTrue(LiveSelectedXiPanel.parse(null).isEmpty());
        assertTrue(LiveSelectedXiPanel.parse("").isEmpty());
        assertTrue(LiveSelectedXiPanel.parse("null").isEmpty());
    }
}
