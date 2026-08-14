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
                      "supported_parameters": ["temperature", "tools"]
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
        assertEquals("anthropic/claude-sonnet", models.get(1).id());
        assertFalse(models.get(1).tools());
        assertTrue(models.get(1).label().contains("no tools"));
    }

    @Test
    void parseModelsIgnoresBlankIds() {
        List<OpenRouterModelCatalog.Model> models = OpenRouterModelCatalog.parseModels(mapper, """
                { "data": [ { "id": "", "name": "Missing" }, { "name": "No id" } ] }
                """);
        assertTrue(models.isEmpty());
    }
}
