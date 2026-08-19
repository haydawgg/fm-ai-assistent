package com.github.fmaiassistent.service;

import com.sun.jna.platform.win32.Crypt32Util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/**
 * Small Windows-only wrapper around DPAPI for secrets that belong to the
 * current Windows user profile. Non-Windows builds deliberately return empty
 * results so the existing portable properties-file behavior is preserved.
 */
final class WindowsSecretStore {
    private static final String PREFIX = "dpapi:";

    private WindowsSecretStore() {
    }

    static boolean supported() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .startsWith("windows");
    }

    static String protect(String value) {
        if (!supported()) {
            throw new IllegalStateException("Windows DPAPI is unavailable on this operating system");
        }
        if (value == null) {
            throw new IllegalArgumentException("Secret value is required");
        }
        try {
            byte[] protectedBytes = Crypt32Util.cryptProtectData(value.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(protectedBytes);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Could not protect the OpenRouter API key with Windows DPAPI", exception);
        }
    }

    static Optional<String> unprotect(String stored) {
        if (!supported() || stored == null || !stored.startsWith(PREFIX)) {
            return Optional.empty();
        }
        try {
            byte[] protectedBytes = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] clearBytes = Crypt32Util.cryptUnprotectData(protectedBytes);
            return Optional.of(new String(clearBytes, StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
