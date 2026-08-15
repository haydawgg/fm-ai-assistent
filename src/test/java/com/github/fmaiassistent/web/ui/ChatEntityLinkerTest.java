package com.github.fmaiassistent.web.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatEntityLinkerTest {
    @Test
    void findsLongerNamesFirstAndIgnoresShortOnes() {
        List<String> hits = ChatEntityLinker.mentions(
                "Cole Palmer and Ada at Chelsea",
                List.of("Cole Palmer", "Ada"),
                List.of("Chelsea"));
        assertEquals(List.of("Cole Palmer", "Chelsea"), hits);
    }
}
