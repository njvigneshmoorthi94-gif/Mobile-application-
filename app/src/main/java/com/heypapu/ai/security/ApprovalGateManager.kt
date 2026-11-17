package com.heypapu.ai.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class ActionCategory {
    ANSWER_CALL,
    REJECT_CALL,
    SEND_WHATSAPP_REPLY,
    SHARE_INSTAGRAM_CONTENT,
    SHARE_FACEBOOK_CONTENT,
    SUBMIT_JOB_APPLICATION,
    SEND_JOB_EMAIL
}

data class PendingApprovalAction(
    val id: String = UUID.randomUUID().toString(),
    val category: ActionCategory,
    val title: String,
    val tamilPrompt: String,
    val englishPrompt: String,
    val payloadPreview: String,
    val onApproved: () -> Unit,
    val onRejected: () -> Unit = {}
)

/**
 * Enforces strict Human-in-the-Loop security:
 * Hey Papu AI NEVER answers/rejects calls, sends messages, posts social content,
 * or submits job applications without explicit user authorization.
 */
object ApprovalGateManager {
    private val _pendingAction = MutableStateFlow<PendingApprovalAction?>(null)
    val pendingAction: StateFlow<PendingApprovalAction?> = _pendingAction.asStateFlow()

    private val _auditLog = MutableStateFlow<List<String>>(emptyList())
    val auditLog: StateFlow<List<String>> = _auditLog.asStateFlow()

    fun requestApproval(action: PendingApprovalAction) {
        _pendingAction.value = action
        logAudit("AWAITING APPROVAL: ${action.title}")
    }

    fun approveCurrent(): Boolean {
        val current = _pendingAction.value ?: return false
        _pendingAction.value = null
        logAudit("APPROVED BY USER: ${current.title}")
        current.onApproved.invoke()
        return true
    }

    fun rejectCurrent(): Boolean {
        val current = _pendingAction.value ?: return false
        _pendingAction.value = null
        logAudit("DECLINED BY USER: ${current.title}")
        current.onRejected.invoke()
        return true
    }

    fun logAudit(entry: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        _auditLog.value = (listOf("[$timestamp] $entry") + _auditLog.value).take(50)
    }
}
