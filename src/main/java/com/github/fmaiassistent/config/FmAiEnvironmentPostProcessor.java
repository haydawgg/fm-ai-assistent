package com.github.fmaiassistent.config;

import com.github.fmaiassistent.service.AppSettingsService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * File-backed H2 next to the settings file, and chat-model off unless an API key is present.
 */
public class FmAiEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String address = environment.getProperty("server.address", "127.0.0.1").strip().toLowerCase(java.util.Locale.ROOT);
        boolean loopback = address.equals("127.0.0.1") || address.equals("localhost") || address.equals("::1");
        boolean explicitlyAllowed = environment.getProperty(
                "app.network.allow-unauthenticated", Boolean.class, false);
        if (!loopback && !explicitlyAllowed) {
            throw new IllegalStateException(
                    "Refusing to bind the unauthenticated FM AI Assistent UI/MCP server to "
                            + address + ". Set app.network.allow-unauthenticated=true only on a protected network.");
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        if (!environment.containsProperty("spring.datasource.url")) {
            Path dbFile = AppSettingsService.dataDirectory().resolve("fm-ai-assistent-db");
            properties.put("spring.datasource.url",
                    "jdbc:h2:file:" + dbFile.toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1");
        }
        if (!environment.containsProperty("spring.ai.model.chat")) {
            properties.put("spring.ai.model.chat", "none");
        }
        if (!properties.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("fmAiAssistentDefaults", properties));
        }
    }
}
