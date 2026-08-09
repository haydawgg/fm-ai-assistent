package com.github.fmaiassistent.web.ui;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;

import java.util.Map;

@Push
public class AppShell implements AppShellConfigurator {
    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addLink("preconnect", "https://fonts.googleapis.com");
        settings.addLink("preconnect", "https://fonts.gstatic.com", Map.of("crossorigin", "anonymous"));
        settings.addLink(
                "stylesheet",
                "https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700;800&family=Sora:wght@500;600;700;800&display=swap");
    }
}
