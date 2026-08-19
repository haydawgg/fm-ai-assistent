package com.github.fmaiassistent.web.ui;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UiFeedbackTest {
    @Test
    void successAndErrorHelpersApplyTheirVariants() {
        Notification success = UiFeedback.style(new Notification(), NotificationVariant.LUMO_SUCCESS);
        Notification error = UiFeedback.style(new Notification(), NotificationVariant.LUMO_ERROR);

        assertTrue(success.getElement().getThemeList().contains(NotificationVariant.LUMO_SUCCESS.getVariantName()));
        assertTrue(error.getElement().getThemeList().contains(NotificationVariant.LUMO_ERROR.getVariantName()));
    }
}
