package com.github.fmaiassistent.web.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionClubTest {
    @Test
    void canonicalizePrefersExactThenShortestContainsMatch() {
        List<String> names = List.of("KVC Westerlo U21", "KVC Westerlo", "Ajax");
        assertEquals("KVC Westerlo", SessionClub.canonicalize("Westerlo", names));
        assertEquals("KVC Westerlo", SessionClub.canonicalize("kvc westerlo", names));
        assertEquals("", SessionClub.canonicalize("Newell's", names));
    }
}
