package com.eduerp.integrations.notification.internal.listener;

import com.eduerp.integrations.notification.NotificationConstants;
import com.eduerp.integrations.notification.NotificationManagement;
import com.eduerp.modules.access.AccessEvents;
import org.springframework.modulith.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class NotificationEventListeners {

    private final NotificationManagement notifications;

    NotificationEventListeners(NotificationManagement notifications) {
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    void on(AccessEvents.AccountJoinedGroup event) {
        notifications.push(event.accountId(), NotificationConstants.EventTypes.PERMISSION_CHANGED, null);
    }
}
