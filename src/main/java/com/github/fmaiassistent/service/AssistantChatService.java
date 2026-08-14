package com.github.fmaiassistent.service;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AssistantChatService {
    static final int MAX_HISTORY_MESSAGES = 20;
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

    private final AppSettingsService settings;
    private final ToolCallbackProvider tools;
    private final Object clientLock = new Object();
    private String cachedApiKey;
    private String cachedModel;
    private ChatClient client;

    public AssistantChatService(AppSettingsService settings, ToolCallbackProvider tools) {
        this.settings = settings;
        this.tools = tools;
    }

    public boolean configured() {
        return settings.chatConfigured();
    }

    public Flux<String> stream(String userMessage) {
        return stream(List.of(), userMessage);
    }

    public Flux<String> stream(List<ChatTurn> history, String userMessage) {
        if (!configured()) {
            throw new IllegalStateException(
                    "Set an OpenRouter API key in Settings to use in-app chat, or connect Codex/Claude to http://127.0.0.1:8080/mcp");
        }
        return chatClient().prompt().messages(promptMessages(history, userMessage)).stream().content();
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
        synchronized (clientLock) {
            if (client == null
                    || !Objects.equals(apiKey, cachedApiKey)
                    || !Objects.equals(model, cachedModel)) {
                cachedApiKey = apiKey;
                cachedModel = model;
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
                        .defaultSystem(SYSTEM)
                        .defaultToolCallbacks(tools)
                        .build();
            }
            return client;
        }
    }
}
