package com.github.fmaiassistent.chat;

import com.github.fmaiassistent.service.AssistantChatService;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Presentation-independent seam for configured chat providers.
 *
 * <p>Consumers receive a stream of domain events and do not need to know how
 * the provider client is built, how retries are selected, or how tool traces
 * are observed.</p>
 */
public interface ChatProviderPort {
    boolean configured();

    Flux<ChatStreamEvent> streamEvents(
            List<AssistantChatService.ChatTurn> history,
            String userMessage,
            String conversationKey,
            AssistantChatService.ChatGrounding grounding,
            String modelOverride);
}
