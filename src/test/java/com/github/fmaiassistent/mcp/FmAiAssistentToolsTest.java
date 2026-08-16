package com.github.fmaiassistent.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FmAiAssistentToolsTest {

    @Test
    void unknownBooleanDoesNotMatchAnExplicitFalseFilter() {
        assertFalse(FmAiAssistentTools.matchesBoolean(null, false));
        assertFalse(FmAiAssistentTools.matchesBoolean(null, true));
        assertTrue(FmAiAssistentTools.matchesBoolean(null, null));
        assertTrue(FmAiAssistentTools.matchesBoolean(false, false));
        assertTrue(FmAiAssistentTools.matchesBoolean(true, true));
    }
}
