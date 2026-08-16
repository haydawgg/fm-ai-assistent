package com.github.fmaiassistent.web.ui;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;

@Push
public class FmAiAppShell implements AppShellConfigurator {
    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addMetaTag("theme-color", "#0b0f0e");
        settings.addMetaTag("color-scheme", "dark");
    }
}
