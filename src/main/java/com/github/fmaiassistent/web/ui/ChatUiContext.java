package com.github.fmaiassistent.web.ui;

import com.vaadin.flow.server.VaadinSession;

final class ChatUiContext {
    private static final String VIEW_KEY = "chat.ui.view";
    private static final String FILTERS_KEY = "chat.ui.filters";
    private static final String DRAFT_KEY = "chat.ui.draft";

    private ChatUiContext() {
    }

    static void setView(String view) {
        put(VIEW_KEY, view);
    }

    static void setFilters(String filters) {
        put(FILTERS_KEY, filters);
    }

    static String view() {
        return get(VIEW_KEY);
    }

    static String filters() {
        return get(FILTERS_KEY);
    }

    static void setDraft(String draft) {
        String text = draft == null ? "" : draft;
        if (text.length() > 20_000) {
            text = text.substring(0, 20_000);
        }
        put(DRAFT_KEY, text);
    }

    static String draft() {
        return get(DRAFT_KEY);
    }

    private static void put(String key, String value) {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) {
            return;
        }
        session.setAttribute(key, value == null ? "" : value.strip());
    }

    private static String get(String key) {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) {
            return "";
        }
        Object value = session.getAttribute(key);
        return value == null ? "" : String.valueOf(value);
    }
}
