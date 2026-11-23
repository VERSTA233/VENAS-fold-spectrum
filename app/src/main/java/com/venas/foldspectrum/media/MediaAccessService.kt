package com.venas.foldspectrum.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow

/** Only signals session changes. No notification messages are stored or transmitted. */
class MediaAccessService : NotificationListenerService() {
    override fun onListenerConnected() { changes.value += 1 }
    override fun onListenerDisconnected() { changes.value += 1 }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.notification?.category == "transport") changes.value += 1
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.notification?.category == "transport") changes.value += 1
    }
    companion object { val changes = MutableStateFlow(0L) }
}
