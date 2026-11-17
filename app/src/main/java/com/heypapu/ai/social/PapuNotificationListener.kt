package com.heypapu.ai.social

import android.app.Notification
import android.app.RemoteInput
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.heypapu.ai.calls.CallAnnouncementAndControlManager
import com.heypapu.ai.security.SecurityVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapturedSocialNotification(
    val id: String,
    val appName: String,
    val packageName: String,
    val sender: String,
    val message: String,
    val timestamp: Long,
    val replyAction: Notification.Action? = null,
    val remoteInput: RemoteInput? = null
)

/**
 * Permitted NotificationListenerService:
 * Reads incoming messages/notifications ONLY from WhatsApp, WhatsApp Business,
 * Instagram, and Facebook when explicitly enabled by the user in Android Settings.
 */
class PapuNotificationListener : NotificationListenerService() {

    companion object {
        private val SUPPORTED_PACKAGES = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.instagram.android" to "Instagram",
            "com.facebook.katana" to "Facebook"
        )

        private val _notifications = MutableStateFlow<List<CapturedSocialNotification>>(emptyList())
        val notifications: StateFlow<List<CapturedSocialNotification>> = _notifications.asStateFlow()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        val appLabel = SUPPORTED_PACKAGES[pkg] ?: return
        if (sbn.isOngoing) return

        val extras = sbn.notification?.extras ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (sender.isBlank() || text.isBlank()) return

        var foundAction: Notification.Action? = null
        var foundRemoteInput: RemoteInput? = null

        sbn.notification.actions?.forEach { action ->
            action.remoteInputs?.forEach { input ->
                if (input.allowFreeFormInput) {
                    foundAction = action
                    foundRemoteInput = input
                }
            }
        }

        val item = CapturedSocialNotification(
            id = "${pkg}_${sbn.id}_${sbn.postTime}",
            appName = appLabel,
            packageName = pkg,
            sender = sender,
            message = text,
            timestamp = sbn.postTime,
            replyAction = foundAction,
            remoteInput = foundRemoteInput
        )

        _notifications.value = (listOf(item) + _notifications.value).take(40)

        val vault = SecurityVault(applicationContext)
        if (vault.speakIncomingMessages && (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b")) {
            CallAnnouncementAndControlManager.voiceEngineRef?.speak(
                "New $appLabel message from $sender: $text"
            )
        }
    }
}
