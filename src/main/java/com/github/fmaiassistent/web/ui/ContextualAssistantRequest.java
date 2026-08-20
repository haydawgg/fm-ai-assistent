package com.github.fmaiassistent.web.ui;

import java.util.List;

/** Context and prompt starters for the lightweight assistant surface. */
public record ContextualAssistantRequest(PlayerContext context, List<String> prompts) {
    public ContextualAssistantRequest {
        prompts = prompts == null ? List.of() : List.copyOf(prompts);
    }
}
