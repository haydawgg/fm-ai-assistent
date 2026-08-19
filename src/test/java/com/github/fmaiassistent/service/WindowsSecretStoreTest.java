package com.github.fmaiassistent.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsSecretStoreTest {
    @Test
    void protectsAndRestoresSecretsForTheCurrentWindowsUser() {
        assumeTrue(WindowsSecretStore.supported());

        String stored = WindowsSecretStore.protect("sk-test-keep-this-private");

        assertTrue(stored.startsWith("dpapi:"));
        assertTrue(WindowsSecretStore.unprotect(stored).isPresent());
        assertEquals("sk-test-keep-this-private", WindowsSecretStore.unprotect(stored).orElseThrow());
    }
}
