package com.github.fmaiassistent.web.ui;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/** Shared user-facing feedback seam for asynchronous desktop views. */
final class UiFeedback {
    private UiFeedback() {
    }

    static void error(Throwable error, String fallback) {
        Throwable cause = error;
        while (cause != null && cause.getCause() != null
                && (cause.getMessage() == null || cause.getMessage().isBlank())) {
            cause = cause.getCause();
        }
        String message = cause == null || cause.getMessage() == null || cause.getMessage().isBlank()
                ? fallback
                : cause.getMessage();
        error(message, 5000, Notification.Position.MIDDLE);
    }

    static Notification show(String message, int duration, Notification.Position position) {
        return Notification.show(message == null ? "" : message, duration, position);
    }

    static Notification success(String message, int duration, Notification.Position position) {
        return style(show(message, duration, position), NotificationVariant.LUMO_SUCCESS);
    }

    static Notification error(String message, int duration, Notification.Position position) {
        return style(show(message, duration, position), NotificationVariant.LUMO_ERROR);
    }

    static Notification style(Notification notification, NotificationVariant variant) {
        notification.addThemeVariants(variant);
        return notification;
    }
}
