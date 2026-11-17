package com.heypapu.ai.social

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.heypapu.ai.security.ApprovalGateManager

object SocialAssistantManager {

    fun openApp(context: Context, packageName: String, fallbackName: String): String {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            "Opening $fallbackName."
        } else {
            "$fallbackName is not installed on this Android phone."
        }
    }

    /**
     * Executes an Android Notification RemoteInput reply ONLY AFTER
     * ApprovalGateManager confirms user permission.
     */
    fun sendApprovedDirectReply(
        context: Context,
        notification: CapturedSocialNotification,
        approvedReplyText: String
    ): String {
        val action = notification.replyAction
        val remoteInput = notification.remoteInput
        if (action == null || remoteInput == null) {
            return "Direct reply channel expired. Opening ${notification.appName} so you can paste the drafted reply."
        }
        return try {
            val intent = Intent()
            val bundle = Bundle().apply {
                putCharSequence(remoteInput.resultKey, approvedReplyText)
            }
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
            action.actionIntent.send(context, 0, intent)
            ApprovalGateManager.logAudit("Sent approved ${notification.appName} reply to ${notification.sender}")
            "Approved reply sent to ${notification.sender} on ${notification.appName}."
        } catch (e: Exception) {
            "Could not send inline reply: ${e.message}"
        }
    }

    fun shareApprovedTextToApp(
        context: Context,
        packageName: String,
        appLabel: String,
        contentText: String
    ) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, contentText)
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(shareIntent)
            ApprovalGateManager.logAudit("Opened $appLabel composer with approved draft.")
        } catch (_: Exception) {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, contentText)
                },
                "Post to $appLabel"
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(chooser)
        }
    }
}
