package com.github.fmaiassistent.service;

import com.github.fmaiassistent.ai.AiPromptContext;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

@Service
public class AssistantChatService {
    public static final int MAX_HISTORY_MESSAGES = 20;
    static final String DEFAULT_CONVERSATION_KEY = "openrouter-chat";
    private static final String SYSTEM = """
            You are the FM AI Assistent for Football Manager 26.
            Use the fm26_* tools for save data. Call fm26_status first if you are unsure whether RAM is loaded.
            For buys use fm26_transfer_shortlist or fm26_moneyball_shortlist.
            For sells use fm26_sell_shortlist. For wonderkids use fm26_wonderkid_shortlist.
            For the live tactic use fm26_current_tactic. For a first XI use fm26_best_xi; omit tacticSlots to use the RAM formation.
            asking_price=null means unknown, not free.
            Tool money is raw pounds. Convert only when showing display currency.
            """;

    public record ChatTurn(boolean user, String text) {
    }

    public record ToolTrace(String name, String label, String input, String output, long elapsedMs) {
    }

    public record UsageSnapshot(Integer promptTokens, Integer completionTokens) {
        static UsageSnapshot from(ChatResponse response) {
            if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
                return new UsageSnapshot(null, null);
            }
            Usage usage = response.getMetadata().getUsage();
            return new UsageSnapshot(tokenCount(usage.getPromptTokens()), tokenCount(usage.getCompletionTokens()));
        }

        private static Integer tokenCount(Integer value) {
            return value == null || value <= 0 ? null : value;
        }
    }

    public record ChatStreamEvent(Kind kind, String text, ToolTrace trace, UsageSnapshot usage) {
        public ChatStreamEvent(Kind kind, String text) {
            this(kind, text, null, null);
        }

        public enum Kind {
            TOKEN,
            TOOL,
            TOOL_TRACE,
            USAGE,
            REASONING
        }
    }

    private final AppSettingsService settings;
    private final ToolCallbackProvider tools;
    private final AiPromptContext promptContext;
    private final Object clientLock = new Object();
    private String cachedApiKey;
    private String cachedModel;
    private String cachedClub;
    private ChatTone cachedTone;
    private Double cachedTopP;
    private ChatClient client;

    public AssistantChatService(AppSettingsService settings, ToolCallbackProvider tools, AiPromptContext promptContext) {
        this.settings = settings;
        this.tools = tools;
        this.promptContext = promptContext;
    }

    public boolean configured() {
        return settings.chatConfigured();
    }

    public Flux<String> stream(String userMessage) {
        return stream(List.of(), userMessage);
    }

    public Flux<String> stream(List<ChatTurn> history, String userMessage) {
        return streamEvents(history, userMessage, DEFAULT_CONVERSATION_KEY)
                .filter(event -> event.kind() == ChatStreamEvent.Kind.TOKEN)
                .map(ChatStreamEvent::text);
    }

    public Flux<ChatStreamEvent> streamEvents(List<ChatTurn> history, String userMessage, String conversationKey) {
        return streamEvents(history, userMessage, conversationKey, ChatGrounding.empty());
    }

    public Flux<ChatStreamEvent> streamEvents(
            List<ChatTurn> history, String userMessage, String conversationKey, ChatGrounding grounding) {
        return streamEvents(history, userMessage, conversationKey, grounding, null);
    }

    public Flux<ChatStreamEvent> streamEvents(
            List<ChatTurn> history,
            String userMessage,
            String conversationKey,
            ChatGrounding grounding,
            String modelOverride) {
        if (!configured()) {
            throw new IllegalStateException(
                    "Set an OpenRouter API key in Settings to use in-app chat, or connect an MCP client to http://127.0.0.1:8080/mcp");
        }
        if (userMessage == null || userMessage.isBlank()) {
            return Flux.error(new IllegalArgumentException("Message cannot be empty"));
        }
        ChatClient snapshot;
        synchronized (clientLock) {
            snapshot = chatClient(modelOverride);
        }
        String key = conversationKey == null || conversationKey.isBlank()
                ? DEFAULT_CONVERSATION_KEY
                : conversationKey;
        String enriched = promptContext.enrich(key, userMessage);
        Sinks.Many<ChatStreamEvent> sideEvents = Sinks.many().unicast().onBackpressureBuffer();
        ToolCallback[] observed = observing(
                tools.getToolCallbacks(),
                name -> sideEvents.tryEmitNext(new ChatStreamEvent(ChatStreamEvent.Kind.TOOL, name)),
                trace -> sideEvents.tryEmitNext(new ChatStreamEvent(ChatStreamEvent.Kind.TOOL_TRACE, trace.label(), trace, null)));
        Flux<ChatStreamEvent> tokens = snapshot.prompt()
                .system(systemPrompt(grounding == null ? ChatGrounding.empty() : grounding)
                        + "Tone: " + settings.chatTone().instruction() + "\n")
                .messages(promptMessages(history, enriched))
                .toolCallbacks(observed)
                .stream()
                .chatResponse()
                .flatMap(response -> Flux.fromIterable(toStreamEvents(response)))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(400))
                        .maxBackoff(Duration.ofSeconds(4))
                        .filter(AssistantChatService::transientError))
                .doFinally(signal -> sideEvents.tryEmitComplete());
        return Flux.merge(sideEvents.asFlux(), tokens);
    }

    public record ChatGrounding(
            String club,
            String currency,
            String view,
            String filters,
            String gameDate,
            boolean snapshotEmpty,
            boolean snapshotStale,
            String instructions) {
        public static ChatGrounding empty() {
            return new ChatGrounding("", "", "", "", "", false, false, "");
        }
    }

    public static int omittedCount(List<ChatTurn> history) {
        int prior = history == null ? 0 : history.size();
        return Math.max(0, prior - MAX_HISTORY_MESSAGES);
    }

    static ChatStreamEvent toStreamEvent(ChatResponse response) {
        List<ChatStreamEvent> events = toStreamEvents(response);
        return events.isEmpty()
                ? new ChatStreamEvent(ChatStreamEvent.Kind.USAGE, "", null, UsageSnapshot.from(response))
                : events.getLast();
    }

    static List<ChatStreamEvent> toStreamEvents(ChatResponse response) {
        String token = "";
        String reasoning = "";
        if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
            var output = response.getResult().getOutput();
            token = output.getText() == null ? "" : output.getText();
            reasoning = extractReasoning(output.getMetadata());
        }
        if (response != null && response.getResult() != null && response.getResult().getMetadata() != null) {
            reasoning = firstNonBlank(reasoning, extractReasoning(toMap(response.getResult().getMetadata())));
        }
        if (response != null && response.getMetadata() != null) {
            reasoning = firstNonBlank(reasoning, extractReasoning(response.getMetadata()));
        }
        ThinkSplitter.Piece split = ThinkSplitter.splitComplete(token);
        reasoning = firstNonBlank(reasoning, split.reasoning());
        token = split.answer();
        UsageSnapshot usage = UsageSnapshot.from(response);
        List<ChatStreamEvent> events = new ArrayList<>();
        if (!reasoning.isBlank()) {
            events.add(new ChatStreamEvent(ChatStreamEvent.Kind.REASONING, reasoning, null, usage));
        }
        if (!token.isEmpty()) {
            events.add(new ChatStreamEvent(ChatStreamEvent.Kind.TOKEN, token, null, usage));
        } else if (events.isEmpty()) {
            events.add(new ChatStreamEvent(ChatStreamEvent.Kind.USAGE, "", null, usage));
        }
        return events;
    }

    static boolean transientError(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            String message = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase(Locale.ROOT);
            if (message.contains("429") || message.contains("rate limit") || message.contains("503")) {
                return true;
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        return false;
    }

    private static String firstNonBlank(String current, String next) {
        return current == null || current.isBlank() ? (next == null ? "" : next) : current;
    }

    static String extractReasoning(Object node) {
        return extractReasoning(node, 0);
    }

    private static String extractReasoning(Object node, int depth) {
        if (node == null || depth > 6) {
            return "";
        }
        if (node instanceof CharSequence text) {
            return text.toString().isBlank() ? "" : text.toString();
        }
        if (node instanceof Map<?, ?> map) {
            for (String key : List.of(
                    "reasoning", "reasoning_content", "reasoningContent", "thinking",
                    "reasoning_text", "reasoningText")) {
                Object value = map.get(key);
                if (value instanceof CharSequence text && !text.toString().isBlank()) {
                    return text.toString();
                }
            }
            Object details = map.containsKey("reasoning_details")
                    ? map.get("reasoning_details")
                    : map.get("reasoningDetails");
            String fromDetails = extractReasoningDetails(details, depth + 1);
            if (!fromDetails.isBlank()) {
                return fromDetails;
            }
            return "";
        }
        if (node instanceof Iterable<?> items && !(node instanceof CharSequence)) {
            StringBuilder joined = new StringBuilder();
            for (Object item : items) {
                String part = extractReasoning(item, depth + 1);
                if (!part.isBlank()) {
                    if (!joined.isEmpty()) {
                        joined.append('\n');
                    }
                    joined.append(part);
                }
            }
            return joined.toString();
        }
        return "";
    }

    private static String extractReasoningDetails(Object details, int depth) {
        if (!(details instanceof Iterable<?> items)) {
            return extractReasoning(details, depth);
        }
        StringBuilder joined = new StringBuilder();
        for (Object item : items) {
            if (item instanceof Map<?, ?> row) {
                Object text = firstNonNull(row.get("text"), row.get("summary"), row.get("content"));
                if (text != null && !String.valueOf(text).isBlank()) {
                    if (!joined.isEmpty()) {
                        joined.append('\n');
                    }
                    joined.append(text);
                }
            } else {
                String part = extractReasoning(item, depth);
                if (!part.isBlank()) {
                    if (!joined.isEmpty()) {
                        joined.append('\n');
                    }
                    joined.append(part);
                }
            }
        }
        return joined.toString();
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static final class ThinkSplitter {
        private boolean inThink;
        private final StringBuilder pending = new StringBuilder();

        public record Piece(String reasoning, String answer) {
            static final Piece EMPTY = new Piece("", "");
        }

        public static Piece splitComplete(String text) {
            ThinkSplitter splitter = new ThinkSplitter();
            Piece first = splitter.push(text);
            Piece rest = splitter.flush();
            return new Piece(first.reasoning() + rest.reasoning(), first.answer() + rest.answer());
        }

        public Piece push(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return Piece.EMPTY;
            }
            pending.append(chunk);
            StringBuilder reasoning = new StringBuilder();
            StringBuilder answer = new StringBuilder();
            drain(reasoning, answer, false);
            return new Piece(reasoning.toString(), answer.toString());
        }

        public Piece flush() {
            StringBuilder reasoning = new StringBuilder();
            StringBuilder answer = new StringBuilder();
            drain(reasoning, answer, true);
            return new Piece(reasoning.toString(), answer.toString());
        }

        private void drain(StringBuilder reasoning, StringBuilder answer, boolean flush) {
            while (!pending.isEmpty()) {
                if (!inThink) {
                    int start = indexOfIgnoreCase(pending, "<think>");
                    if (start < 0) {
                        if (flush) {
                            answer.append(pending);
                            pending.setLength(0);
                        } else if (partialTag(pending, "<think>")) {
                            break;
                        } else {
                            answer.append(pending);
                            pending.setLength(0);
                        }
                        break;
                    }
                    answer.append(pending, 0, start);
                    pending.delete(0, start + 7);
                    inThink = true;
                } else {
                    int end = indexOfIgnoreCase(pending, "</think>");
                    if (end < 0) {
                        if (flush || !partialTag(pending, "</think>")) {
                            reasoning.append(pending);
                            pending.setLength(0);
                        }
                        break;
                    }
                    reasoning.append(pending, 0, end);
                    pending.delete(0, end + 8);
                    inThink = false;
                }
            }
        }

        private static boolean partialTag(StringBuilder buffer, String tag) {
            String text = buffer.toString();
            int start = Math.max(0, text.length() - tag.length() + 1);
            String tail = text.substring(start).toLowerCase(Locale.ROOT);
            String needle = tag.toLowerCase(Locale.ROOT);
            for (int length = 1; length < needle.length(); length++) {
                if (tail.endsWith(needle.substring(0, length))) {
                    return true;
                }
            }
            return false;
        }

        private static int indexOfIgnoreCase(StringBuilder buffer, String needle) {
            return buffer.toString().toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
        }
    }

    private static String reasoningText(Map<String, Object> metadata) {
        return extractReasoning(metadata);
    }

    private static Map<String, Object> toMap(org.springframework.ai.chat.metadata.ChatGenerationMetadata metadata) {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        if (metadata == null || metadata.isEmpty()) {
            return map;
        }
        for (var entry : metadata.entrySet()) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    static ToolCallback[] observing(ToolCallback[] callbacks, Consumer<String> onTool) {
        return observing(callbacks, onTool, ignored -> {
        });
    }

    static ToolCallback[] observing(
            ToolCallback[] callbacks, Consumer<String> onTool, Consumer<ToolTrace> onTrace) {
        if (callbacks == null || callbacks.length == 0) {
            return new ToolCallback[0];
        }
        ToolCallback[] wrapped = new ToolCallback[callbacks.length];
        for (int index = 0; index < callbacks.length; index++) {
            wrapped[index] = new ObservingToolCallback(callbacks[index], onTool, onTrace);
        }
        return wrapped;
    }

    static List<Message> promptMessages(List<ChatTurn> history, String userMessage) {
        List<ChatTurn> prior = history == null ? List.of() : history;
        int start = Math.max(0, prior.size() - MAX_HISTORY_MESSAGES);
        List<Message> messages = new ArrayList<>();
        if (start > 0) {
            messages.add(new UserMessage(compactSummary(prior.subList(0, start))));
        }
        for (int index = start; index < prior.size(); index++) {
            ChatTurn turn = prior.get(index);
            if (turn == null || turn.text() == null || turn.text().isBlank()) {
                continue;
            }
            messages.add(turn.user() ? new UserMessage(turn.text()) : new AssistantMessage(turn.text()));
        }
        messages.add(new UserMessage(userMessage));
        return messages;
    }

    static String compactSummary(List<ChatTurn> omitted) {
        StringBuilder summary = new StringBuilder(
                "Earlier conversation summary (not sent in full). Use it only as background:\n");
        int used = 0;
        for (ChatTurn turn : omitted) {
            if (turn == null || turn.text() == null || turn.text().isBlank()) {
                continue;
            }
            String line = (turn.user() ? "User: " : "Assistant: ") + turn.text().strip().replaceAll("\\s+", " ");
            if (line.length() > 220) {
                line = line.substring(0, 217) + "…";
            }
            summary.append("- ").append(line).append('\n');
            used++;
            if (used >= 8) {
                summary.append("- …").append(omitted.size() - used).append(" more turns omitted.\n");
                break;
            }
        }
        return summary.toString();
    }

    private ChatClient chatClient() {
        return chatClient(null);
    }

    private ChatClient chatClient(String modelOverride) {
        String apiKey = settings.openRouterApiKey();
        String model = modelOverride == null || modelOverride.isBlank()
                ? settings.openRouterModel()
                : modelOverride.strip();
        String club = settings.sessionClub();
        ChatTone tone = settings.chatTone();
        Double topP = settings.chatTopP();
        synchronized (clientLock) {
            if (client == null
                    || !Objects.equals(apiKey, cachedApiKey)
                    || !Objects.equals(model, cachedModel)
                    || !Objects.equals(club, cachedClub)
                    || cachedTone != tone
                    || !Objects.equals(topP, cachedTopP)) {
                cachedApiKey = apiKey;
                cachedModel = model;
                cachedClub = club;
                cachedTone = tone;
                cachedTopP = topP;
                OpenAiChatOptions options = chatOptions(apiKey, model, tone, topP);
                OpenAIOkHttpClient.Builder syncBuilder = OpenAIOkHttpClient.builder()
                        .apiKey(apiKey)
                        .baseUrl(OpenRouterModelCatalog.BASE_URL)
                        .putHeader("HTTP-Referer", OpenRouterModelCatalog.HTTP_REFERER)
                        .putHeader("X-Title", OpenRouterModelCatalog.APP_TITLE);
                OpenAIOkHttpClientAsync.Builder asyncBuilder = OpenAIOkHttpClientAsync.builder()
                        .apiKey(apiKey)
                        .baseUrl(OpenRouterModelCatalog.BASE_URL)
                        .putHeader("HTTP-Referer", OpenRouterModelCatalog.HTTP_REFERER)
                        .putHeader("X-Title", OpenRouterModelCatalog.APP_TITLE);
                OpenAiChatModel chatModel = OpenAiChatModel.builder()
                        .openAiClient(syncBuilder.build())
                        .openAiClientAsync(asyncBuilder.build())
                        .options(options)
                        .build();
                client = ChatClient.builder(chatModel)
                        .defaultSystem(systemPrompt(club))
                        .build();
            }
            return client;
        }
    }

    static final class ObservingToolCallback implements ToolCallback {
        private static final int MAX_TRACE_CHARS = 4000;
        private final ToolCallback delegate;
        private final Consumer<String> onTool;
        private final Consumer<ToolTrace> onTrace;

        ObservingToolCallback(ToolCallback delegate, Consumer<String> onTool, Consumer<ToolTrace> onTrace) {
            this.delegate = delegate;
            this.onTool = onTool;
            this.onTrace = onTrace;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return invoke(toolInput, () -> delegate.call(toolInput));
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return invoke(toolInput, () -> delegate.call(toolInput, toolContext));
        }

        private String invoke(String toolInput, java.util.function.Supplier<String> action) {
            notifyStart();
            long started = System.nanoTime();
            String output = action.get();
            if (onTrace != null) {
                String name = toolName();
                onTrace.accept(new ToolTrace(
                        name,
                        labelForTool(name),
                        clip(toolInput),
                        clip(output),
                        Math.max(0, (System.nanoTime() - started) / 1_000_000L)));
            }
            return output;
        }

        private void notifyStart() {
            if (onTool == null) {
                return;
            }
            onTool.accept(labelForTool(toolName()));
        }

        private String toolName() {
            ToolDefinition definition = delegate.getToolDefinition();
            return definition == null ? null : definition.name();
        }

        private static String clip(String value) {
            if (value == null) {
                return "";
            }
            return value.length() <= MAX_TRACE_CHARS ? value : value.substring(0, MAX_TRACE_CHARS) + "…";
        }
    }

    static OpenAiChatOptions chatOptions(String apiKey, String model) {
        return chatOptions(apiKey, model, ChatTone.DETAILED);
    }

    static OpenAiChatOptions chatOptions(String apiKey, String model, ChatTone tone) {
        return chatOptions(apiKey, model, tone, null);
    }

    static OpenAiChatOptions chatOptions(String apiKey, String model, ChatTone tone, Double topP) {
        ChatTone resolved = tone == null ? ChatTone.DETAILED : tone;
        java.util.HashMap<String, Object> extra = new java.util.HashMap<>();
        extra.put("include_reasoning", Boolean.TRUE);
        var builder = OpenAiChatOptions.builder()
                .model(model)
                .apiKey(apiKey)
                .baseUrl(OpenRouterModelCatalog.BASE_URL)
                .temperature(resolved.temperature())
                .extraBody(extra)
                .customHeaders(Map.of(
                        "HTTP-Referer", OpenRouterModelCatalog.HTTP_REFERER,
                        "X-Title", OpenRouterModelCatalog.APP_TITLE));
        if (topP != null && topP > 0 && topP <= 1) {
            builder.topP(topP);
        }
        return builder.build();
    }

    static String systemPrompt(String sessionClub) {
        return systemPrompt(new ChatGrounding(sessionClub, "", "", "", "", false, false, ""));
    }

    static String systemPrompt(ChatGrounding grounding) {
        ChatGrounding facts = grounding == null ? ChatGrounding.empty() : grounding;
        StringBuilder prompt = new StringBuilder(SYSTEM);
        if (facts.club() != null && !facts.club().isBlank()) {
            prompt.append("The manager's club this session is ").append(facts.club().strip()).append(".\n");
        }
        if (facts.currency() != null && !facts.currency().isBlank()) {
            prompt.append("Display currency is ").append(facts.currency().strip())
                    .append(". Tool money is still raw pounds.\n");
        }
        if (facts.view() != null && !facts.view().isBlank()) {
            prompt.append("The manager is on the ").append(facts.view().strip()).append(" view");
            if (facts.filters() != null && !facts.filters().isBlank()) {
                prompt.append(" (").append(facts.filters().strip()).append(')');
            }
            prompt.append(".\n");
        }
        if (facts.snapshotEmpty()) {
            prompt.append("No RAM snapshot is loaded. Tell the user to load from RAM before using save data.\n");
        } else if (facts.gameDate() != null && !facts.gameDate().isBlank()) {
            prompt.append("Snapshot game date is ").append(facts.gameDate().strip()).append('.');
            if (facts.snapshotStale()) {
                prompt.append(" It is stale — suggest reloading RAM if the save has moved on.");
            }
            prompt.append('\n');
        }
        if (facts.instructions() != null && !facts.instructions().isBlank()) {
            prompt.append("Custom instructions:\n").append(facts.instructions().strip()).append('\n');
        }
        return prompt.toString();
    }

    static String labelForTool(String name) {
        if (name == null || name.isBlank()) {
            return "Working";
        }
        return switch (name) {
            case "fm26_status" -> "Checking snapshot";
            case "fm26_find_players" -> "Searching players";
            case "fm26_get_player_details" -> "Opening player";
            case "fm26_get_club_context" -> "Reading club";
            case "fm26_transfer_shortlist" -> "Searching shortlist";
            case "fm26_moneyball_shortlist" -> "Ranking value signings";
            case "fm26_wonderkid_shortlist" -> "Searching wonderkids";
            case "fm26_sell_shortlist" -> "Ranking sales";
            case "fm26_best_xi" -> "Picking first XI";
            case "fm26_current_tactic" -> "Reading live tactic";
            case "fm26_compare_squads" -> "Comparing squads";
            case "fm26_compare_players" -> "Comparing players";
            case "fm26_load_from_ram" -> "Loading from RAM";
            default -> name.startsWith("fm26_") ? name.substring(5).replace('_', ' ') : name;
        };
    }
}
