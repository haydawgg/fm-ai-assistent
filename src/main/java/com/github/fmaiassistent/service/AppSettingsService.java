package com.github.fmaiassistent.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.github.fmaiassistent.FmAiAssistentApplication;
import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import com.github.fmaiassistent.repository.PlayerFilterCriteria;
import com.github.fmaiassistent.web.ui.SavedPlayerView;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

@Service
public class AppSettingsService {
    private static final String SETTINGS_FILE = "fm-ai-assistent.properties";
    private static final String SETTINGS_FILE_PROPERTY = "fmaiassistent.settings.file";
    private static final String CURRENCY_KEY = "currency";
    private static final String PLAYER_VIEWS_KEY = "player.views";
    private static final String OPENAI_API_KEY = "openai.api.key";
    private static final String OPENAI_MODEL_KEY = "openai.model";
    private static final String DEFAULT_OPENAI_MODEL = "gpt-4.1-mini";

    private final Path settingsPath;
    private final ObjectMapper objectMapper;

    public AppSettingsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.settingsPath = resolveSettingsPath().toAbsolutePath().normalize();
        ensureSettingsFileExists();
    }

    public MoneyCurrency currency() {
        return MoneyCurrency.fromPropertyValue(load().getProperty(CURRENCY_KEY));
    }

    public void saveCurrency(MoneyCurrency currency) {
        Properties properties = load();
        properties.setProperty(CURRENCY_KEY, (currency == null ? MoneyCurrency.POUND : currency).propertyValue());
        save(properties);
    }

    public List<SavedPlayerView> playerViews() {
        String json = load().getProperty(PLAYER_VIEWS_KEY, "[]");
        try {
            List<SavedPlayerView> views = objectMapper.readValue(json, new TypeReference<>() {
            });
            if (views == null || views.isEmpty()) {
                return List.of();
            }
            return views.stream()
                    .filter(view -> view != null && view.name() != null && !view.name().isBlank())
                    .map(this::normalizeView)
                    .sorted(Comparator.comparing(view -> view.name().toLowerCase()))
                    .toList();
        } catch (JacksonException ex) {
            return List.of();
        }
    }

    public void savePlayerView(SavedPlayerView view) {
        if (view == null || view.name() == null || view.name().isBlank()) {
            throw new IllegalArgumentException("View name is required");
        }
        SavedPlayerView normalized = normalizeView(view);
        List<SavedPlayerView> views = new ArrayList<>(playerViews());
        views.removeIf(existing -> existing.name().equalsIgnoreCase(normalized.name()));
        views.add(normalized);
        views.sort(Comparator.comparing(item -> item.name().toLowerCase()));
        writePlayerViews(views);
    }

    public void deletePlayerView(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        List<SavedPlayerView> views = new ArrayList<>(playerViews());
        boolean removed = views.removeIf(existing -> existing.name().equalsIgnoreCase(name));
        if (removed) {
            writePlayerViews(views);
        }
    }

    public Path settingsPath() {
        return settingsPath;
    }

    public static Path dataDirectory() {
        return applicationDirectory();
    }

    public String openaiApiKey() {
        String value = load().getProperty(OPENAI_API_KEY, "");
        if (value == null || value.isBlank()) {
            String env = System.getenv("OPENAI_API_KEY");
            return env == null ? "" : env;
        }
        return value;
    }

    public String openaiModel() {
        String value = load().getProperty(OPENAI_MODEL_KEY, DEFAULT_OPENAI_MODEL);
        return value == null || value.isBlank() ? DEFAULT_OPENAI_MODEL : value;
    }

    public void saveOpenAi(String apiKey, String model) {
        Properties properties = load();
        if (apiKey == null || apiKey.isBlank()) {
            properties.remove(OPENAI_API_KEY);
        } else {
            properties.setProperty(OPENAI_API_KEY, apiKey.trim());
        }
        properties.setProperty(OPENAI_MODEL_KEY, model == null || model.isBlank() ? DEFAULT_OPENAI_MODEL : model.trim());
        save(properties);
    }

    public boolean chatConfigured() {
        return !openaiApiKey().isBlank();
    }

    private void writePlayerViews(List<SavedPlayerView> views) {
        Properties properties = load();
        try {
            properties.setProperty(PLAYER_VIEWS_KEY, objectMapper.writeValueAsString(views));
        } catch (JacksonException ex) {
            throw new IllegalStateException("Could not serialize player views", ex);
        }
        save(properties);
    }

    private SavedPlayerView normalizeView(SavedPlayerView view) {
        PlayerFilterCriteria filter = view.filter() == null ? PlayerFilterCriteria.empty() : view.filter();
        Map<String, Integer> positions = filter.positionMinimums() == null ? Map.of() : Map.copyOf(filter.positionMinimums());
        Map<String, Integer> attributes = filter.attributeMinimums() == null ? Map.of() : Map.copyOf(filter.attributeMinimums());
        PlayerFilterCriteria normalizedFilter = new PlayerFilterCriteria(
                nullToEmpty(filter.name()),
                nullToEmpty(filter.gender()),
                nullToEmpty(filter.playingNation()),
                nullToEmpty(filter.playingCompetition()),
                nullToEmpty(filter.club()),
                filter.ageMin(),
                filter.ageMax(),
                filter.heightMin(),
                filter.heightMax(),
                nullToEmpty(filter.nationality()),
                filter.currentReputationMin(),
                filter.currentReputationMax(),
                filter.homeReputationMin(),
                filter.homeReputationMax(),
                filter.worldReputationMin(),
                filter.worldReputationMax(),
                filter.caMin(),
                filter.caMax(),
                filter.paMin(),
                filter.paMax(),
                filter.contractEndDateFrom(),
                filter.contractEndDateTo(),
                filter.askingPriceMin(),
                filter.askingPriceMax(),
                filter.salaryMax(),
                positions,
                attributes);
        return new SavedPlayerView(view.name().trim(), normalizedFilter, view.showAllColumns());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Properties load() {
        Properties properties = new Properties();
        if (!Files.exists(settingsPath)) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(settingsPath)) {
            properties.load(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load settings from " + settingsPath, ex);
        }
        return properties;
    }

    private void save(Properties properties) {
        try {
            Path parent = settingsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(settingsPath)) {
                properties.store(output, "FM AI Assistent settings");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save settings to " + settingsPath, ex);
        }
    }

    private void ensureSettingsFileExists() {
        if (Files.exists(settingsPath)) {
            return;
        }
        Properties properties = new Properties();
        properties.setProperty(CURRENCY_KEY, MoneyCurrency.POUND.propertyValue());
        properties.setProperty(PLAYER_VIEWS_KEY, "[]");
        save(properties);
    }

    private static Path resolveSettingsPath() {
        String explicitPath = System.getProperty(SETTINGS_FILE_PROPERTY);
        if (explicitPath != null && !explicitPath.isBlank()) {
            return Path.of(explicitPath);
        }
        return applicationDirectory().resolve(SETTINGS_FILE);
    }

    private static Path applicationDirectory() {
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
                return Files.isRegularFile(location) ? location.getParent() : location;
            }
        } catch (URISyntaxException | RuntimeException ignored) {
        }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath();
    }

    private static Optional<Path> currentProcessCommand() {
        try {
            return ProcessHandle.current()
                    .info()
                    .command()
                    .filter(value -> !value.isBlank())
                    .map(value -> Path.of(value).toAbsolutePath())
                    .filter(Files::isRegularFile);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static boolean isJavaLauncher(Path command) {
        String fileName = command.getFileName() == null ? "" : command.getFileName().toString();
        return fileName.equals("java") || fileName.equals("java.exe") || fileName.equals("javaw") || fileName.equals("javaw.exe");
    }

    private static Optional<Path> jarFromJavaCommand() {
        String command = System.getProperty("sun.java.command");
        if (command == null || command.isBlank()) {
            return Optional.empty();
        }
        String firstToken = command.trim().split("\\s+", 2)[0];
        if (!firstToken.endsWith(".jar")) {
            return Optional.empty();
        }
        Path jar = Path.of(firstToken).toAbsolutePath().normalize();
        return Files.isRegularFile(jar) ? Optional.of(jar) : Optional.empty();
    }
}
