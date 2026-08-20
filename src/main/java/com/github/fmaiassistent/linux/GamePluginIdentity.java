package com.github.fmaiassistent.linux;

import com.github.fmaiassistent.memory.ProcessMemoryReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable identity for the native FM module that owns the build-specific layouts. */
public record GamePluginIdentity(String path, String sha256, long size) {
    public GamePluginIdentity {
        path = path == null ? "" : path;
        sha256 = sha256 == null ? "" : sha256.toLowerCase(java.util.Locale.ROOT);
        size = Math.max(0L, size);
    }

    public static GamePluginIdentity unknown() {
        return new GamePluginIdentity("", "", 0L);
    }

    public static GamePluginIdentity detect(ProcessMemoryReader reader) {
        if (reader == null) {
            return unknown();
        }
        try {
            String path = FmOffsets.gamePluginPath(reader).orElse("");
            if (path.isBlank()) {
                return unknown();
            }
            Path file = Path.of(path);
            if (!Files.isRegularFile(file)) {
                return new GamePluginIdentity(path, "", 0L);
            }
            return new GamePluginIdentity(path, sha256(file), Files.size(file));
        } catch (IOException | RuntimeException ex) {
            return unknown();
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public boolean isKnown() {
        return !sha256.isBlank();
    }
}
