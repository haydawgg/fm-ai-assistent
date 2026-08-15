package com.github.fmaiassistent.web.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatSlashCommandsTest {
    @Test
    void expandsKnownCommands() {
        assertEquals("Build my best XI from the live formation", ChatSlashCommands.expand("/xi").orElseThrow());
        assertEquals("Find affordable wonderkids for my club", ChatSlashCommands.expand("/wonderkids").orElseThrow());
        assertTrue(ChatSlashCommands.expand("hello").isEmpty());
    }

    @Test
    void buyWithRestAddsPosition() {
        assertEquals(
                "For my club, find affordable left back signings using the save data.",
                ChatSlashCommands.expand("/buy left back").orElseThrow());
    }
}
