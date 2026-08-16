package com.github.fmaiassistent.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.github.fmaiassistent.FmAiAssistentApplication;
import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import com.github.fmaiassistent.repository.PlayerFilterCriteria;
import com.github.fmaiassistent.web.ui.SavedChatPrompt;
import com.github.fmaiassistent.web.ui.SavedPlayerView;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

@Service
public class AppSettingsService {
    private static final Logger LOG = LoggerFactory.getLogger(AppSettingsService.class);
    private static final String SETTINGS_FILE = "fm-ai-assistent.properties";
    private static final String SETTINGS_FILE_PROPERTY = "fmaiassistent.settings.file";
    private static final String CURRENCY_KEY = "currency";
    private static final String SESSION_CLUB_KEY = "session.club";
    private static final String PLAYER_VIEWS_KEY = "player.views";
    private static final String OPENROUTER_API_KEY = "openrouter.api.key";
    private static final String OPENROUTER_MODEL_KEY = "openrouter.model";
    private static final String CHAT_SESSION_KEY = "chat.session.id";
    private static final String CHAT_PROMPTS_KEY = "chat.prompts";
    private static final String CHAT_INSTRUCTIONS_KEY = "chat.instructions";
    private static final String CHAT_DAILY_CAP_KEY = "chat.daily.cap.usd";
    private static final String OPENROUTER_FALLBACK_KEY = "openrouter.fallback.model";
    private static final String OPENROUTER_FALLBACK_MODELS_KEY = "openrouter.fallback.models";
    private static final String CHAT_NOTIFY_KEY = "chat.notify.desktop";
    private static final String CHAT_TONE_KEY = "chat.tone";
    private static final String PINNED_MODELS_KEY = "openrouter.models.pinned";
    private static final String ONBOARDING_KEY = "onboarding.complete";
    private static final String CHAT_TOP_P_KEY = "chat.top.p";
    private static final String LEGACY_OPENAI_API_KEY = "openai.api.key";
    private static final String LEGACY_OPENAI_MODEL_KEY = "openai.model";

    private final Path settingsPath;
    private final ObjectMapper objectMapper;
    private final Object settingsLock = new Object();

    public AppSettingsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.settingsPath = resolveSettingsPath().toAbsolutePath().normalize();
        ensureSettingsFileExists();
    }

    public MoneyCurrency currency() {
        synchronized (settingsLock) {
            return MoneyCurrency.fromPropertyValue(load().getProperty(CURRENCY_KEY));
        }
    }

    public void saveCurrency(MoneyCurrency currency) {
        synchronized (settingsLock) {
            Properties properties = load();
            properties.setProperty(CURRENCY_KEY, (currency == null ? MoneyCurrency.POUND : currency).propertyValue());
            save(properties);
        }
    }

    public String sessionClub() {
        synchronized (settingsLock) {
            String value = load().getProperty(SESSION_CLUB_KEY, "");
            return value == null ? "" : value.strip();
        }
    }

    public void saveSessionClub(String club) {
        synchronized (settingsLock) {
            Properties properties = load();
            if (club == null || club.isBlank()) {
                properties.remove(SESSION_CLUB_KEY);
            } else {
                properties.setProperty(SESSION_CLUB_KEY, club.strip());
            }
            save(properties);
        }
    }

    public List<SavedPlayerView> playerViews() {
        synchronized (settingsLock) {
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
    }

    public void savePlayerView(SavedPlayerView view) {
        if (view == null || view.name() == null || view.name().isBlank()) {
            throw new IllegalArgumentException("View name is required");
        }
        synchronized (settingsLock) {
            SavedPlayerView normalized = normalizeView(view);
            List<SavedPlayerView> views = new ArrayList<>(playerViews());
            views.removeIf(existing -> existing.name().equalsIgnoreCase(normalized.name()));
            views.add(normalized);
            views.sort(Comparator.comparing(item -> item.name().toLowerCase()));
            writePlayerViews(views);
        }
    }

    public void deletePlayerView(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        synchronized (settingsLock) {
            List<SavedPlayerView> views = new ArrayList<>(playerViews());
            boolean removed = views.removeIf(existing -> existing.name().equalsIgnoreCase(name));
            if (removed) {
                writePlayerViews(views);
            }
        }
    }

    public Path settingsPath() {
        return settingsPath;
    }

    public static Path dataDirectory() {
        return applicationDirectory();
    }

    public String openRouterApiKey() {
        synchronized (settingsLock) {
            Properties properties = load();
            String value = firstNonBlank(
                    properties.getProperty(OPENROUTER_API_KEY, ""),
                    properties.getProperty(LEGACY_OPENAI_API_KEY, ""),
                    System.getenv("OPENROUTER_API_KEY"),
                    System.getenv("OPENAI_API_KEY"));
            return value == null ? "" : value;
        }
    }

    public String openRouterModel() {
        synchronized (settingsLock) {
            Properties properties = load();
            String value = firstNonBlank(
                    properties.getProperty(OPENROUTER_MODEL_KEY, ""),
                    properties.getProperty(LEGACY_OPENAI_MODEL_KEY, ""));
            if (value == null || value.isBlank()) {
                return OpenRouterModelCatalog.DEFAULT_MODEL;
            }
            return value.contains("/") ? value : "openai/" + value;
        }
    }

    public void saveOpenRouter(String apiKey, String model) {
        synchronized (settingsLock) {
            Properties properties = load();
            if (apiKey == null || apiKey.isBlank()) {
                properties.remove(OPENROUTER_API_KEY);
            } else {
                properties.setProperty(OPENROUTER_API_KEY, apiKey.trim());
            }
            properties.setProperty(OPENROUTER_MODEL_KEY,
                    model == null || model.isBlank() ? OpenRouterModelCatalog.DEFAULT_MODEL : model.trim());
            properties.remove(LEGACY_OPENAI_API_KEY);
            properties.remove(LEGACY_OPENAI_MODEL_KEY);
            save(properties);
        }
    }

    public boolean chatConfigured() {
        return !openRouterApiKey().isBlank();
    }

    public String chatInstructions() {
        synchronized (settingsLock) {
            String value = load().getProperty(CHAT_INSTRUCTIONS_KEY, "");
            return value == null ? "" : value;
        }
    }

    public void saveChatInstructions(String instructions) {
        synchronized (settingsLock) {
            Properties properties = load();
            if (instructions == null || instructions.isBlank()) {
                properties.remove(CHAT_INSTRUCTIONS_KEY);
            } else {
                properties.setProperty(CHAT_INSTRUCTIONS_KEY, instructions.strip());
            }
            save(properties);
        }
    }

    public double dailySpendCapUsd() {
        synchronized (settingsLock) {
            String value = load().getProperty(CHAT_DAILY_CAP_KEY, "0");
            if (value == null || value.isBlank()) {
                return 0;
            }
            try {
                return Math.max(0, Double.parseDouble(value.strip()));
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
    }

    public void saveDailySpendCapUsd(Double capUsd) {
        synchronized (settingsLock) {
            Properties properties = load();
            if (capUsd == null || capUsd <= 0) {
                properties.remove(CHAT_DAILY_CAP_KEY);
            } else {
                properties.setProperty(CHAT_DAILY_CAP_KEY, Double.toString(capUsd));
            }
            save(properties);
        }
    }

    public String openRouterFallbackModel() {
        List<String> models = openRouterFallbackModels();
        return models.isEmpty() ? "" : models.getFirst();
    }

    public List<String> openRouterFallbackModels() {
        synchronized (settingsLock) {
            Properties properties = load();
            return parseFallbackModels(
                    objectMapper,
                    properties.getProperty(OPENROUTER_FALLBACK_MODELS_KEY, ""),
                    properties.getProperty(OPENROUTER_FALLBACK_KEY, ""));
        }
    }

    static List<String> parseFallbackModels(ObjectMapper mapper, String json, String legacy) {
        List<String> ids = new ArrayList<>();
        if (json != null && !json.isBlank()) {
            try {
                List<String> parsed = mapper.readValue(json, new TypeReference<>() {
                });
                if (parsed != null) {
                    for (String id : parsed) {
                        if (id != null && !id.isBlank() && !ids.contains(id.strip())) {
                            ids.add(id.strip());
                        }
                    }
                }
            } catch (JacksonException ignored) {
            }
        }
        if (ids.isEmpty() && legacy != null && !legacy.isBlank()) {
            ids.add(legacy.strip());
        }
        return List.copyOf(ids);
    }

    public void saveOpenRouterFallbackModel(String model) {
        saveOpenRouterFallbackModels(model == null || model.isBlank() ? List.of() : List.of(model.strip()));
    }

    public void saveOpenRouterFallbackModels(List<String> models) {
        synchronized (settingsLock) {
            Properties properties = load();
            List<String> ids = models == null ? List.of() : models.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(String::strip)
                    .distinct()
                    .toList();
            if (ids.isEmpty()) {
                properties.remove(OPENROUTER_FALLBACK_KEY);
                properties.remove(OPENROUTER_FALLBACK_MODELS_KEY);
            } else {
                properties.setProperty(OPENROUTER_FALLBACK_KEY, ids.getFirst());
                try {
                    properties.setProperty(OPENROUTER_FALLBACK_MODELS_KEY, objectMapper.writeValueAsString(ids));
                } catch (JacksonException ex) {
                    throw new IllegalStateException("Could not serialize fallback models", ex);
                }
            }
            save(properties);
        }
    }

    public Boolean desktopNotify() {
        synchronized (settingsLock) {
            String value = load().getProperty(CHAT_NOTIFY_KEY, "");
            if (value == null || value.isBlank()) {
                return null;
            }
            return "true".equalsIgnoreCase(value.strip());
        }
    }

    public void saveDesktopNotify(Boolean enabled) {
        synchronized (settingsLock) {
            Properties properties = load();
            if (enabled == null) {
                properties.remove(CHAT_NOTIFY_KEY);
            } else {
                properties.setProperty(CHAT_NOTIFY_KEY, Boolean.toString(enabled));
            }
            save(properties);
        }
    }

    public ChatTone chatTone() {
        synchronized (settingsLock) {
            return ChatTone.fromProperty(load().getProperty(CHAT_TONE_KEY, ""));
        }
    }

    public void saveChatTone(ChatTone tone) {
        synchronized (settingsLock) {
            Properties properties = load();
            properties.setProperty(CHAT_TONE_KEY, (tone == null ? ChatTone.DETAILED : tone).name());
            save(properties);
        }
    }

    public List<String> pinnedModels() {
        synchronized (settingsLock) {
            String json = load().getProperty(PINNED_MODELS_KEY, "[]");
            try {
                List<String> ids = objectMapper.readValue(json, new TypeReference<>() {
                });
                if (ids == null || ids.isEmpty()) {
                    return List.of();
                }
                return ids.stream().filter(id -> id != null && !id.isBlank()).map(String::strip).distinct().toList();
            } catch (JacksonException ex) {
                return List.of();
            }
        }
    }

    public void togglePinnedModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return;
        }
        synchronized (settingsLock) {
            List<String> ids = new ArrayList<>(pinnedModels());
            String id = modelId.strip();
            if (!ids.remove(id)) {
                ids.add(id);
            }
            Properties properties = load();
            try {
                properties.setProperty(PINNED_MODELS_KEY, objectMapper.writeValueAsString(ids));
            } catch (JacksonException ex) {
                throw new IllegalStateException("Could not serialize pinned models", ex);
            }
            save(properties);
        }
    }

    public boolean onboardingComplete() {
        synchronized (settingsLock) {
            return "true".equalsIgnoreCase(load().getProperty(ONBOARDING_KEY, ""));
        }
    }

    public void saveOnboardingComplete(boolean complete) {
        synchronized (settingsLock) {
            Properties properties = load();
            properties.setProperty(ONBOARDING_KEY, Boolean.toString(complete));
            save(properties);
        }
    }

    public Double chatTopP() {
        synchronized (settingsLock) {
            String value = load().getProperty(CHAT_TOP_P_KEY, "");
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                double parsed = Double.parseDouble(value.strip());
                if (parsed <= 0 || parsed > 1) {
                    return null;
                }
                return parsed;
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    public void saveChatTopP(Double topP) {
        synchronized (settingsLock) {
            Properties properties = load();
            if (topP == null || topP <= 0 || topP > 1) {
                properties.remove(CHAT_TOP_P_KEY);
            } else {
                properties.setProperty(CHAT_TOP_P_KEY, Double.toString(topP));
            }
            save(properties);
        }
    }

    public String lastChatSessionId() {
        synchronized (settingsLock) {
            String value = load().getProperty(CHAT_SESSION_KEY, "");
            return value == null ? "" : value.strip();
        }
    }

    public void saveLastChatSessionId(String sessionId) {
        synchronized (settingsLock) {
            Properties properties = load();
            if (sessionId == null || sessionId.isBlank()) {
                properties.remove(CHAT_SESSION_KEY);
            } else {
                properties.setProperty(CHAT_SESSION_KEY, sessionId.strip());
            }
            save(properties);
        }
    }

    public List<SavedChatPrompt> chatPrompts() {
        synchronized (settingsLock) {
            String json = load().getProperty(CHAT_PROMPTS_KEY, "[]");
            try {
                List<SavedChatPrompt> prompts = objectMapper.readValue(json, new TypeReference<>() {
                });
                if (prompts == null || prompts.isEmpty()) {
                    return List.of();
                }
                return prompts.stream()
                        .filter(prompt -> prompt != null && prompt.name() != null && !prompt.name().isBlank()
                                && prompt.text() != null && !prompt.text().isBlank())
                        .toList();
            } catch (JacksonException ex) {
                return List.of();
            }
        }
    }

    public void saveChatPrompt(SavedChatPrompt prompt) {
        if (prompt == null || prompt.name() == null || prompt.name().isBlank()
                || prompt.text() == null || prompt.text().isBlank()) {
            throw new IllegalArgumentException("Prompt name and text are required");
        }
        synchronized (settingsLock) {
            List<SavedChatPrompt> prompts = new ArrayList<>(chatPrompts());
            prompts.removeIf(existing -> existing.name().equalsIgnoreCase(prompt.name().strip()));
            prompts.add(new SavedChatPrompt(prompt.name().strip(), prompt.text().strip()));
            writeChatPrompts(prompts);
        }
    }

    public void deleteChatPrompt(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        synchronized (settingsLock) {
            List<SavedChatPrompt> prompts = new ArrayList<>(chatPrompts());
            boolean removed = prompts.removeIf(existing -> existing.name().equalsIgnoreCase(name));
            if (removed) {
                writeChatPrompts(prompts);
            }
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private void writeChatPrompts(List<SavedChatPrompt> prompts) {
        Properties properties = load();
        try {
            properties.setProperty(CHAT_PROMPTS_KEY, objectMapper.writeValueAsString(prompts));
        } catch (JacksonException ex) {
            throw new IllegalStateException("Could not serialize chat prompts", ex);
        }
        save(properties);
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
        Map<String, Integer> positions = filter.positionMinimums() == null ? Map.of() : copyNonNull(filter.positionMinimums());
        Map<String, Integer> attributes = filter.attributeMinimums() == null ? Map.of() : copyNonNull(filter.attributeMinimums());
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

    private static <V> Map<String, V> copyNonNull(Map<String, V> source) {
        Map<String, V> result = new HashMap<>();
        source.forEach((key, value) -> {
            if (value != null) {
                result.put(key, value);
            }
        });
        return result;
    }

    private Properties load() {
        Properties properties = new Properties();
        if (!Files.exists(settingsPath)) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(settingsPath)) {
            properties.load(input);
        } catch (IOException ex) {
            backupCorruptSettings(ex);
            return properties;
        }
        return properties;
    }

    private void backupCorruptSettings(IOException cause) {
        LOG.error("Settings file {} is unreadable; backing it up and starting with defaults", settingsPath, cause);
        try {
            Path backup = settingsPath.resolveSibling(
                    settingsPath.getFileName() + ".corrupt-" + System.currentTimeMillis());
            Files.move(settingsPath, backup);
            LOG.info("Corrupt settings backed up to {}", backup);
        } catch (IOException backupError) {
            LOG.error("Could not back up corrupt settings file {}", settingsPath, backupError);
        }
    }

    private void save(Properties properties) {
        try {
            Path parent = settingsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = settingsPath.resolveSibling(settingsPath.getFileName() + ".tmp");
            try (OutputStream output = Files.newOutputStream(temp)) {
                properties.store(output, "FM AI Assistent settings");
            }
            try {
                Files.move(temp, settingsPath,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, settingsPath, StandardCopyOption.REPLACE_EXISTING);
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
