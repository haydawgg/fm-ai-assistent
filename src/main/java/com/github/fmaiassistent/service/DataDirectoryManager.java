package com.github.fmaiassistent.service;

import com.github.fmaiassistent.FmAiAssistentApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.Optional;

/**
 * Owns the on-disk data seam for the desktop application.
 *
 * User data must not live beside an installed executable: that location can be
 * read-only, replaced during upgrades, or shared by multiple versions. The
 * first access migrates the legacy files into the stable per-user directory
 * without deleting the originals.
 */
public final class DataDirectoryManager {
    private static final Logger LOG = LoggerFactory.getLogger(DataDirectoryManager.class);
    private static final String DATA_DIRECTORY_PROPERTY = "fmaiassistent.data.directory";
    private static final String SETTINGS_FILE = "fm-ai-assistent.properties";
    private static final String DATABASE_FILE = "fm-ai-assistent-db.mv.db";
    private static final String TRACE_FILE = "fm-ai-assistent-db.trace.db";
    private static final String MIGRATION_MARKER = ".legacy-migration-v1";

    private static final Object LOCK = new Object();
    private static volatile Path resolved;

    private DataDirectoryManager() {
    }

    public static Path dataDirectory() {
        Path current = resolved;
        if (current != null) {
            return current;
        }
        synchronized (LOCK) {
            if (resolved == null) {
                resolved = resolveAndMigrate();
            }
            return resolved;
        }
    }

    private static Path resolveAndMigrate() {
        String explicit = System.getProperty(DATA_DIRECTORY_PROPERTY);
        Path target = explicit == null || explicit.isBlank()
                ? Path.of(System.getProperty("user.home"), ".fm-ai-assistent")
                : Path.of(explicit);
        target = target.toAbsolutePath().normalize();
        try {
            Files.createDirectories(target);
            migrateLegacyFiles(target);
            return target;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not prepare FM AI data directory " + target, ex);
        }
    }

    private static void migrateLegacyFiles(Path target) throws IOException {
        Path marker = target.resolve(MIGRATION_MARKER);
        if (Files.exists(marker)) {
            return;
        }
        Path legacy = legacyApplicationDirectory().toAbsolutePath().normalize();
        if (legacy.equals(target) || !Files.isDirectory(legacy)) {
            Files.createFile(marker);
            return;
        }

        Path backupDirectory = target.resolve("backups").resolve("pre-migration-" + System.currentTimeMillis());
        boolean backedUp = false;
        for (String fileName : new String[]{SETTINGS_FILE, DATABASE_FILE, TRACE_FILE}) {
            Path source = legacy.resolve(fileName);
            Path destination = target.resolve(fileName);
            if (!Files.isRegularFile(source) || Files.exists(destination)) {
                continue;
            }
            if (!backedUp) {
                Files.createDirectories(backupDirectory);
                backedUp = true;
            }
            Files.copy(source, backupDirectory.resolve(fileName), StandardCopyOption.COPY_ATTRIBUTES);
            Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
            LOG.info("Migrated legacy FM AI data file {} to {}", source, destination);
        }
        Files.createFile(marker);
        if (backedUp) {
            LOG.info("Legacy data backup retained at {}", backupDirectory);
        }
    }

    private static Path legacyApplicationDirectory() {
        Optional<Path> nativeExecutable = currentProcessCommand()
                .filter(command -> !isJavaLauncher(command));
        if (nativeExecutable.isPresent()) {
            return nativeExecutable.get().getParent();
        }
        Optional<Path> jar = jarFromJavaCommand();
        if (jar.isPresent()) {
            return jar.get().getParent();
        }
        try {
            CodeSource codeSource = FmAiAssistentApplication.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                Path location = Path.of(codeSource.getLocation().toURI()).toAbsolutePath();
                if (Files.isRegularFile(location)) {
                    return location.getParent();
                }
            }
        } catch (URISyntaxException | RuntimeException ignored) {
            // Fall through to the stable per-user location.
        }
        return Path.of(System.getProperty("user.home"), ".fm-ai-assistent");
    }

    private static Optional<Path> currentProcessCommand() {
        try {
            return ProcessHandle.current().info().command()
                    .filter(value -> !value.isBlank())
                    .map(value -> Path.of(value).toAbsolutePath())
                    .filter(Files::isRegularFile);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static boolean isJavaLauncher(Path command) {
        String fileName = command.getFileName() == null ? "" : command.getFileName().toString();
        return fileName.equalsIgnoreCase("java")
                || fileName.equalsIgnoreCase("java.exe")
                || fileName.equalsIgnoreCase("javaw")
                || fileName.equalsIgnoreCase("javaw.exe");
    }

    private static Optional<Path> jarFromJavaCommand() {
        String command = System.getProperty("sun.java.command");
        if (command == null || command.isBlank()) {
            return Optional.empty();
        }
        String firstToken = command.trim().split("\\s+", 2)[0];
        if (!firstToken.toLowerCase().endsWith(".jar")) {
            return Optional.empty();
        }
        Path jar = Path.of(firstToken).toAbsolutePath().normalize();
        return Files.isRegularFile(jar) ? Optional.of(jar) : Optional.empty();
    }
}
