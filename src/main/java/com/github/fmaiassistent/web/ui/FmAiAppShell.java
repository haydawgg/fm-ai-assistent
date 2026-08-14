package com.github.fmaiassistent.web.ui;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;

import java.util.Map;

@Push
public class FmAiAppShell implements AppShellConfigurator {
    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addLink("preconnect", "https://fonts.googleapis.com");
        settings.addLink("https://fonts.gstatic.com", Map.of("rel", "preconnect", "crossorigin", "anonymous"));
        settings.addLink(
                "stylesheet",
                "https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700;800&family=Sora:wght@500;600;700;800&display=swap");
        settings.addMetaTag("theme-color", "#0b0f0e");
        settings.addMetaTag("color-scheme", "dark");
    }
}
