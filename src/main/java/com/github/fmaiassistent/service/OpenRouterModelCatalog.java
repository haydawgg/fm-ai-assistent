package com.github.fmaiassistent.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class OpenRouterModelCatalog {
    public static final String BASE_URL = "https://openrouter.ai/api/v1";
    public static final String DEFAULT_MODEL = "nvidia/nemotron-3.5-lightning:free";
    public static final String HTTP_REFERER = "https://github.com/haydawgg/fm-ai-assistent";
    public static final String APP_TITLE = "FM AI Assistent";

    private static final Logger log = LoggerFactory.getLogger(OpenRouterModelCatalog.class);
    private static final URI MODELS_URI = URI.create(BASE_URL + "/models?output_modalities=text");
    private static final URI MODELS_FALLBACK_URI = URI.create(BASE_URL + "/models");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ExecutorService fetchExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService refreshExecutor = Executors.newSingleThreadScheduledExecutor(thread -> {
        Thread scheduled = new Thread(thread, "openrouter-models-refresh");
        scheduled.setDaemon(true);
        return scheduled;
    });

    private volatile List<Model> cached = List.of();
    private volatile Instant fetchedAt = Instant.EPOCH;
    private volatile String lastError;

    public OpenRouterModelCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @PostConstruct
    void start() {
        refreshAsync();
        refreshExecutor.scheduleAtFixedRate(this::refreshQuietly, 1, 1, TimeUnit.HOURS);
    }

    @PreDestroy
    void stop() {
        refreshExecutor.shutdownNow();
        fetchExecutor.shutdownNow();
    }

    public List<Model> cachedModels() {
        return cached;
    }

    public Instant fetchedAt() {
        return fetchedAt;
    }

    public String lastError() {
        return lastError;
    }

    public CompletableFuture<List<Model>> refreshAsync() {
        return CompletableFuture.supplyAsync(this::refreshNow, fetchExecutor);
    }

    public record Model(
            String id,
            String name,
            boolean tools,
            Integer contextLength,
            Double promptPerToken,
            Double completionPerToken) {
        public String label() {
            String display = name == null || name.isBlank() ? id : name;
            StringBuilder label = new StringBuilder(display);
            String ctx = formatContext(contextLength);
            if (!ctx.isEmpty()) {
                label.append(" · ").append(ctx);
            }
            String price = formatPrice(id, promptPerToken, completionPerToken);
            if (!price.isEmpty()) {
                label.append(" · ").append(price);
            }
            if (!tools) {
                label.append(" (no tools)");
            }
            return label.toString();
        }
    }

    static List<Model> parseModels(ObjectMapper mapper, String json) {
        JsonNode root = mapper.readTree(json);
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return List.of();
        }
        List<Model> models = new ArrayList<>();
        for (JsonNode item : data) {
            String id = text(item, "id");
            if (id.isBlank() || !outputsText(item)) {
                continue;
            }
            String name = text(item, "name");
            models.add(new Model(
                    id,
                    name.isBlank() ? id : name,
                    supportsTools(item),
                    contextLength(item),
                    rate(item.path("pricing").path("prompt")),
                    rate(item.path("pricing").path("completion"))));
        }
        models.sort(Comparator
                .comparing((Model model) -> !model.tools())
                .thenComparing(model -> model.name().toLowerCase(Locale.ROOT))
                .thenComparing(model -> model.id().toLowerCase(Locale.ROOT)));
        return List.copyOf(models);
    }

    private List<Model> refreshNow() {
        try {
            String body = download(MODELS_URI);
            List<Model> models = parseModels(objectMapper, body);
            if (models.isEmpty()) {
                models = parseModels(objectMapper, download(MODELS_FALLBACK_URI));
            }
            if (models.isEmpty()) {
                throw new IllegalStateException("OpenRouter returned no text models");
            }
            cached = models;
            fetchedAt = Instant.now();
            lastError = null;
            log.info("Loaded {} OpenRouter models", models.size());
            return models;
        } catch (RuntimeException | IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            lastError = rootMessage(ex);
            log.warn("Could not refresh OpenRouter models: {}", lastError);
            if (cached.isEmpty()) {
                throw new IllegalStateException(lastError, ex);
            }
            return cached;
        }
    }

    private void refreshQuietly() {
        try {
            refreshNow();
        } catch (RuntimeException ignored) {
        }
    }

    private String download(URI uri) throws IOException, InterruptedException {
        return download(uri, null);
    }

    private String download(URI uri, String apiKey) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", APP_TITLE)
                .header("HTTP-Referer", HTTP_REFERER)
                .header("X-Title", APP_TITLE)
                .GET();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OpenRouter models HTTP " + response.statusCode());
        }
        return response.body() == null ? "" : response.body();
    }

    public ProbeResult probe(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return new ProbeResult(false, "Enter an OpenRouter API key first.");
        }
        try {
            String body = download(MODELS_URI, apiKey.strip());
            List<Model> models = parseModels(objectMapper, body);
            if (models.isEmpty()) {
                return new ProbeResult(false, "Key accepted but OpenRouter returned no text models.");
            }
            return new ProbeResult(true, "Connected · " + models.size() + " text models");
        } catch (RuntimeException | IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new ProbeResult(false, rootMessage(ex));
        }
    }

    public record ProbeResult(boolean ok, String message) {
    }

    public Double estimateUsd(String modelId, Integer promptTokens, Integer completionTokens) {
        return estimateUsd(cached, modelId, promptTokens, completionTokens);
    }

    public Integer estimatePromptTokens(String... parts) {
        int chars = 0;
        if (parts != null) {
            for (String part : parts) {
                if (part != null) {
                    chars += part.length();
                }
            }
        }
        return Math.max(1, chars / 4);
    }

    static String formatContext(Integer contextLength) {
        if (contextLength == null || contextLength <= 0) {
            return "";
        }
        if (contextLength >= 1000) {
            long thousands = Math.round(contextLength / 1000.0);
            return thousands + "k ctx";
        }
        return contextLength + " ctx";
    }

    static String formatPrice(String id, Double promptPerToken, Double completionPerToken) {
        boolean taggedFree = id != null && id.toLowerCase(Locale.ROOT).contains(":free");
        boolean zeroRates = (promptPerToken == null || promptPerToken == 0)
                && (completionPerToken == null || completionPerToken == 0);
        if (taggedFree || (promptPerToken != null && completionPerToken != null && zeroRates)) {
            return "Free";
        }
        if (promptPerToken == null || promptPerToken <= 0) {
            return "";
        }
        double perMillion = promptPerToken * 1_000_000d;
        if (perMillion >= 0.01) {
            return String.format(Locale.US, "$%.2f/M", perMillion);
        }
        return String.format(Locale.US, "$%.4f/M", perMillion);
    }

    static Double estimateUsd(List<Model> models, String modelId, Integer promptTokens, Integer completionTokens) {
        if (modelId == null || modelId.isBlank() || models == null) {
            return null;
        }
        for (Model model : models) {
            if (!modelId.equals(model.id())) {
                continue;
            }
            double cost = 0;
            boolean any = false;
            if (model.promptPerToken() != null && promptTokens != null && promptTokens > 0) {
                cost += model.promptPerToken() * promptTokens;
                any = true;
            }
            if (model.completionPerToken() != null && completionTokens != null && completionTokens > 0) {
                cost += model.completionPerToken() * completionTokens;
                any = true;
            }
            return any ? cost : null;
        }
        return null;
    }

    private static Integer contextLength(JsonNode item) {
        JsonNode node = item.get("context_length");
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            int value = Integer.parseInt(node.asText().strip());
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double rate(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            double value = Double.parseDouble(node.asText().strip());
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean outputsText(JsonNode item) {
        JsonNode outputs = item.path("architecture").path("output_modalities");
        if (!outputs.isArray() || outputs.isEmpty()) {
            return true;
        }
        for (JsonNode modality : outputs) {
            if ("text".equalsIgnoreCase(modality.asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean supportsTools(JsonNode item) {
        JsonNode parameters = item.path("supported_parameters");
        if (!parameters.isArray()) {
            return false;
        }
        for (JsonNode parameter : parameters) {
            if ("tools".equalsIgnoreCase(parameter.asText())) {
                return true;
            }
        }
        return false;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText().strip();
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName()
                : cause.getMessage();
    }
}
