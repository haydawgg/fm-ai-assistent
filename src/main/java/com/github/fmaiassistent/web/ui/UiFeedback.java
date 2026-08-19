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
        Notification.show(message, 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
