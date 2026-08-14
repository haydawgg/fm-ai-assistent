package com.github.fmaiassistent.service;

import com.github.fmaiassistent.ai.AiPromptContext;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Service
public class AssistantChatService {
    static final int MAX_HISTORY_MESSAGES = 20;
    static final String DEFAULT_CONVERSATION_KEY = "openrouter-chat";
    private static final String SYSTEM = """
            You are the FM AI Assistent for Football Manager 26.
            Use the fm26_* tools for save data. Call fm26_status first if you are unsure whether RAM is loaded.
            For buys use fm26_transfer_shortlist or fm26_moneyball_shortlist.
            For sells use fm26_sell_shortlist. For wonderkids use fm26_wonderkid_shortlist.
            For the live tactic use fm26_current_tactic. For a first XI use fm26_best_xi; omit tacticSlots to use the RAM formation.
            asking_price=null means unknown, not free.
            """;

    public record ChatTurn(boolean user, String text) {
    }

    public record ChatStreamEvent(Kind kind, String text) {
        public enum Kind {
            TOKEN,
            TOOL
        }
    }

    private final AppSettingsService settings;
    private final ToolCallbackProvider tools;
    private final AiPromptContext promptContext;
    private final Object clientLock = new Object();
    private String cachedApiKey;
    private String cachedModel;
    private String cachedClub;
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
        if (!configured()) {
            throw new IllegalStateException(
                    "Set an OpenRouter API key in Settings to use in-app chat, or connect an MCP client to http://127.0.0.1:8080/mcp");
        }
        if (userMessage == null || userMessage.isBlank()) {
            return Flux.error(new IllegalArgumentException("Message cannot be empty"));
        }
        ChatClient snapshot;
        synchronized (clientLock) {
            snapshot = chatClient();
        }
        String key = conversationKey == null || conversationKey.isBlank()
                ? DEFAULT_CONVERSATION_KEY
                : conversationKey;
        String enriched = promptContext.enrich(key, userMessage);
        Sinks.Many<ChatStreamEvent> toolEvents = Sinks.many().unicast().onBackpressureBuffer();
        ToolCallback[] observed = observing(tools.getToolCallbacks(), name ->
                toolEvents.tryEmitNext(new ChatStreamEvent(ChatStreamEvent.Kind.TOOL, name)));
        Flux<ChatStreamEvent> tokens = snapshot.prompt()
                .messages(promptMessages(history, enriched))
                .toolCallbacks(observed)
                .stream()
                .content()
                .map(token -> new ChatStreamEvent(ChatStreamEvent.Kind.TOKEN, token))
                .doFinally(signal -> toolEvents.tryEmitComplete());
        return Flux.merge(toolEvents.asFlux(), tokens);
    }

    static ToolCallback[] observing(ToolCallback[] callbacks, Consumer<String> onTool) {
        if (callbacks == null || callbacks.length == 0) {
            return new ToolCallback[0];
        }
        ToolCallback[] wrapped = new ToolCallback[callbacks.length];
        for (int index = 0; index < callbacks.length; index++) {
            wrapped[index] = new ObservingToolCallback(callbacks[index], onTool);
        }
        return wrapped;
    }

    static List<Message> promptMessages(List<ChatTurn> history, String userMessage) {
        List<ChatTurn> prior = history == null ? List.of() : history;
        int start = Math.max(0, prior.size() - MAX_HISTORY_MESSAGES);
        List<Message> messages = new ArrayList<>();
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

    private ChatClient chatClient() {
        String apiKey = settings.openRouterApiKey();
        String model = settings.openRouterModel();
        String club = settings.sessionClub();
        synchronized (clientLock) {
            if (client == null
                    || !Objects.equals(apiKey, cachedApiKey)
                    || !Objects.equals(model, cachedModel)
                    || !Objects.equals(club, cachedClub)) {
                cachedApiKey = apiKey;
                cachedModel = model;
                cachedClub = club;
                OpenAiChatModel chatModel = OpenAiChatModel.builder()
                        .openAiClient(OpenAIOkHttpClient.builder()
                                .apiKey(apiKey)
                                .baseUrl(OpenRouterModelCatalog.BASE_URL)
                                .putHeader("HTTP-Referer", OpenRouterModelCatalog.HTTP_REFERER)
                                .putHeader("X-Title", OpenRouterModelCatalog.APP_TITLE)
                                .build())
                        .options(OpenAiChatOptions.builder().model(model).build())
                        .build();
                client = ChatClient.builder(chatModel)
                        .defaultSystem(systemPrompt(club))
                        .build();
            }
            return client;
        }
    }

    static final class ObservingToolCallback implements ToolCallback {
        private final ToolCallback delegate;
        private final Consumer<String> onTool;

        ObservingToolCallback(ToolCallback delegate, Consumer<String> onTool) {
            this.delegate = delegate;
            this.onTool = onTool;
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
            notifyStart();
            return delegate.call(toolInput);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            notifyStart();
            return delegate.call(toolInput, toolContext);
        }

        private void notifyStart() {
            if (onTool == null) {
                return;
            }
            ToolDefinition definition = delegate.getToolDefinition();
            String name = definition == null ? null : definition.name();
            onTool.accept(labelForTool(name));
        }
    }

    static String systemPrompt(String sessionClub) {
        if (sessionClub == null || sessionClub.isBlank()) {
            return SYSTEM;
        }
        return SYSTEM + "The manager's club this session is " + sessionClub.strip() + ".\n";
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
