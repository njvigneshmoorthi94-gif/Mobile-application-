package com.heypapu.ai.calls

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.heypapu.ai.security.ActionCategory
import com.heypapu.ai.security.ApprovalGateManager
import com.heypapu.ai.security.PendingApprovalAction
import com.heypapu.ai.voice.BilingualVoiceEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IncomingCallInfo(
    val phoneNumber: String,
    val resolvedName: String?,
    val displayIdentity: String,
    val isKnownContact: Boolean,
    val spamRiskLevel: String,
    val spamWarningReason: String,
    val timestamp: Long = System.currentTimeMillis()
)

object CallAnnouncementAndControlManager {

    private val _activeCall = MutableStateFlow<IncomingCallInfo?>(null)
    val activeCall: StateFlow<IncomingCallInfo?> = _activeCall.asStateFlow()

    private val _speakerphoneEnabled = MutableStateFlow(false)
    val speakerphoneEnabled: StateFlow<Boolean> = _speakerphoneEnabled.asStateFlow()

    var voiceEngineRef: BilingualVoiceEngine? = null

    fun onIncomingCallScreened(context: Context, info: IncomingCallInfo) {
        _activeCall.value = info

        val statusNote = if (info.isKnownContact) {
            "Known contact."
        } else {
            "Unknown caller. ${info.spamWarningReason}"
        }

        val announcement =
            "Sir, you have an incoming call from ${info.displayIdentity}. $statusNote Sir, shall I answer this call?"

        voiceEngineRef?.speak(announcement, "en-IN")

        ApprovalGateManager.requestApproval(
            PendingApprovalAction(
                category = ActionCategory.ANSWER_CALL,
                title = "Incoming Call: ${info.displayIdentity}",
                tamilPrompt = "சார், ${info.displayIdentity} இடமிருந்து அழைப்பு வருகிறது. அழைப்பை ஏற்கலாமா?",
                englishPrompt = announcement,
                payloadPreview = "Caller: ${info.displayIdentity} (${info.phoneNumber}) | Status: $statusNote",
                onApproved = {
                    answerCallWithPermission(context)
                },
                onRejected = {
                    rejectCallWithPermission(context)
                }
            )
        )
    }

    @SuppressLint("MissingPermission")
    fun answerCallWithPermission(context: Context): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "Cannot answer: ANSWER_PHONE_CALLS permission not granted in Android Settings."
        }
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            @Suppress("DEPRECATION")
            telecomManager.acceptRingingCall()
            ApprovalGateManager.logAudit("Call answered after explicit user approval.")
            "Call answered. You can now speak to the caller."
        } catch (e: Exception) {
            "Android call control restricted by device OEM: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    fun rejectCallWithPermission(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "Unsupported Android version."
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "Cannot end/reject call: ANSWER_PHONE_CALLS permission not granted."
        }
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            @Suppress("DEPRECATION")
            telecomManager.endCall()
            _activeCall.value = null
            ApprovalGateManager.logAudit("Call ended/rejected after explicit user approval.")
            "Call rejected/ended."
        } catch (e: Exception) {
            "Unable to end call: ${e.message}"
        }
    }

    fun toggleSpeakerphone(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val next = !audioManager.isSpeakerphoneOn
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = next
        _speakerphoneEnabled.value = next
        return next
    }
}
