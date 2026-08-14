package com.github.fmaiassistent.service;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AssistantChatService {
    private static final String SYSTEM = """
            You are the FM AI Assistent for Football Manager 26.
            Use the fm26_* tools for save data. Call fm26_status first if you are unsure whether RAM is loaded.
            For buys use fm26_transfer_shortlist or fm26_moneyball_shortlist.
            For sells use fm26_sell_shortlist. For wonderkids use fm26_wonderkid_shortlist.
            For a pasted tactic use fm26_best_xi. Money values are raw pounds.
            asking_price=null means unknown, not free.
            """;

    private final AppSettingsService settings;
    private final ToolCallbackProvider tools;

    public AssistantChatService(AppSettingsService settings, ToolCallbackProvider tools) {
        this.settings = settings;
        this.tools = tools;
    }

    public boolean configured() {
        return settings.chatConfigured();
    }

    public Flux<String> stream(String userMessage) {
        if (!configured()) {
            throw new IllegalStateException("Set an OpenAI API key in Settings to use in-app chat, or connect Codex/Claude to http://127.0.0.1:8080/mcp");
        }
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiClient(OpenAIOkHttpClient.builder().apiKey(settings.openaiApiKey()).build())
                .options(OpenAiChatOptions.builder().model(settings.openaiModel()).build())
                .build();
        ChatClient client = ChatClient.builder(model)
                .defaultSystem(SYSTEM)
                .defaultToolCallbacks(tools)
                .build();
        return client.prompt().user(userMessage).stream().content();
    }
}
