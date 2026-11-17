package com.heypapu.ai.calls

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import androidx.core.content.ContextCompat

/**
 * Official Android 10+ CallScreeningService:
 * - Identifies incoming callers ONLY from permitted Contacts or verified network caller ID.
 * - Never claims to know a caller's identity without reliable verification.
 * - Never answers, rejects, or records calls automatically without explicit user approval.
 */
class PapuCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val handle: Uri? = callDetails.handle
        val rawNumber = handle?.schemeSpecificPart?.trim().orEmpty()

        val contactName = if (rawNumber.isNotEmpty()) lookupContactName(rawNumber) else null
        val isKnownContact = !contactName.isNullOrBlank()

        val verificationStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            callDetails.callerNumberVerificationStatus
        } else {
            Connection.VERIFICATION_STATUS_NOT_VERIFIED
        }

        val spamRisk = evaluateSpamRisk(rawNumber, isKnownContact, verificationStatus)
        val displayIdentity = when {
            isKnownContact -> contactName!!
            rawNumber.isNotBlank() -> rawNumber
            else -> "Private / Hidden Number"
        }

        val callInfo = IncomingCallInfo(
            phoneNumber = rawNumber.ifBlank { "Hidden Number" },
            resolvedName = contactName,
            displayIdentity = displayIdentity,
            isKnownContact = isKnownContact,
            spamRiskLevel = spamRisk.first,
            spamWarningReason = spamRisk.second
        )

        CallAnnouncementAndControlManager.onIncomingCallScreened(applicationContext, callInfo)

        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)
    }

    private fun lookupContactName(phoneNumber: String): String? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun evaluateSpamRisk(
        number: String,
        isKnown: Boolean,
        verificationStatus: Int
    ): Pair<String, String> {
        if (isKnown) return Pair("SAFE", "Saved in your contacts.")
        val digitsOnly = number.filter { it.isDigit() }
        return when {
            digitsOnly.isEmpty() ->
                Pair("HIGH_WARNING", "Private/withheld caller ID. Identity cannot be verified.")
            digitsOnly.startsWith("140") || digitsOnly.startsWith("160") || digitsOnly.startsWith("91140") ->
                Pair("SPAM_LIKELY", "Registered telemarketing / promotional prefix (140/160).")
            verificationStatus == Connection.VERIFICATION_STATUS_FAILED ->
                Pair("HIGH_WARNING", "Network caller ID verification failed (possible spoofing).")
            else ->
                Pair("UNKNOWN", "Unknown number not in your contacts. Identity unverified.")
        }
    }
}
