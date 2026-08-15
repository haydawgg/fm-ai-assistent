package com.github.fmaiassistent.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRouterModelCatalogTest {
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void parseModelsKeepsTextModelsAndPrefersTools() {
        List<OpenRouterModelCatalog.Model> models = OpenRouterModelCatalog.parseModels(mapper, """
                {
                  "data": [
                    {
                      "id": "openai/gpt-4o-mini",
                      "name": "GPT-4o Mini",
                      "architecture": { "output_modalities": ["text"] },
                      "supported_parameters": ["temperature", "tools"],
                      "context_length": 128000,
                      "pricing": { "prompt": "0.00000015", "completion": "0.0000006" }
                    },
                    {
                      "id": "acme/image-only",
                      "name": "Image Only",
                      "architecture": { "output_modalities": ["image"] },
                      "supported_parameters": ["temperature"]
                    },
                    {
                      "id": "anthropic/claude-sonnet",
                      "name": "Claude Sonnet",
                      "architecture": { "output_modalities": ["text"] },
                      "supported_parameters": ["temperature"]
                    }
                  ]
                }
                """);
        assertEquals(2, models.size());
        assertEquals("openai/gpt-4o-mini", models.get(0).id());
        assertTrue(models.get(0).tools());
        assertEquals(0.00000015, models.get(0).promptPerToken());
        assertEquals(128000, models.get(0).contextLength());
        assertTrue(models.get(0).label().contains("128k ctx"));
        assertTrue(models.get(0).label().contains("$0.15/M"));
        assertEquals("anthropic/claude-sonnet", models.get(1).id());
        assertFalse(models.get(1).tools());
        assertTrue(models.get(1).label().contains("no tools"));
    }

    @Test
    void estimateUsdUsesPromptAndCompletionRates() {
        List<OpenRouterModelCatalog.Model> models = OpenRouterModelCatalog.parseModels(mapper, """
                { "data": [{
                  "id": "openai/gpt-4o-mini",
                  "name": "GPT-4o Mini",
                  "architecture": { "output_modalities": ["text"] },
                  "supported_parameters": ["tools"],
                  "pricing": { "prompt": "0.00000015", "completion": "0.0000006" }
                }] }
                """);
        double cost = OpenRouterModelCatalog.estimateUsd(models, "openai/gpt-4o-mini", 1000, 500);
        assertEquals(0.00045, cost, 1e-12);
    }

    @Test
    void labelMarksFreeModels() {
        List<OpenRouterModelCatalog.Model> models = OpenRouterModelCatalog.parseModels(mapper, """
                { "data": [{
                  "id": "nvidia/nemotron-3.5-lightning:free",
                  "name": "Nemotron",
                  "architecture": { "output_modalities": ["text"] },
                  "supported_parameters": ["tools"],
                  "context_length": 262144,
                  "pricing": { "prompt": "0", "completion": "0" }
                }] }
                """);
        assertEquals("Nemotron · 262k ctx · Free", models.getFirst().label());
    }

    @Test
    void parseModelsIgnoresBlankIds() {
        List<OpenRouterModelCatalog.Model> models = OpenRouterModelCatalog.parseModels(mapper, """
                { "data": [ { "id": "", "name": "Missing" }, { "name": "No id" } ] }
                """);
        assertTrue(models.isEmpty());
    }
}
