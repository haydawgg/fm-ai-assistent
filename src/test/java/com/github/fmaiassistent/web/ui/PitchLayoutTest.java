package com.github.fmaiassistent.web.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PitchLayoutTest {

    @Test
    void spreadsDuplicateCentreBacksAcrossTheLine() {
        List<PitchLayout.Slot> slots = PitchLayout.layout(List.of("DC", "DC", "DC"));
        assertEquals(3, slots.size());
        assertEquals(slots.get(0).yPercent(), slots.get(1).yPercent());
        assertEquals(slots.get(1).yPercent(), slots.get(2).yPercent());
        assertEquals(34.0, slots.get(0).xPercent(), 0.01);
        assertEquals(50.0, slots.get(1).xPercent(), 0.01);
        assertEquals(66.0, slots.get(2).xPercent(), 0.01);
    }

    @Test
    void leavesASingleSlotOnItsBase() {
        List<PitchLayout.Slot> slots = PitchLayout.layout(List.of("GK"));
        assertEquals(50.0, slots.get(0).xPercent(), 0.01);
        assertEquals(90.0, slots.get(0).yPercent(), 0.01);
    }
}
