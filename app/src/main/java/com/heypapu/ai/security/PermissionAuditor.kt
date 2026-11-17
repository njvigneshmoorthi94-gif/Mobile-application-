package com.heypapu.ai.security

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.heypapu.ai.social.PapuNotificationListener

data class PermissionAuditItem(
    val title: String,
    val permissionKey: String,
    val isGranted: Boolean,
    val explanationEn: String,
    val explanationTa: String,
    val isSpecialSetting: Boolean = false
)

object PermissionAuditor {

    fun getRuntimePermissionsToRequest(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.toTypedArray()
    }

    fun auditAll(context: Context): List<PermissionAuditItem> {
        fun check(perm: String) =
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

        val roleManager = context.getSystemService(RoleManager::class.java)
        val hasCallScreeningRole = roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true

        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: ""
        val myListener = ComponentName(context, PapuNotificationListener::class.java).flattenToString()
        val hasNotificationAccess = enabledListeners.contains(myListener)

        return listOf(
            PermissionAuditItem(
                title = "Microphone (Voice Commands)",
                permissionKey = Manifest.permission.RECORD_AUDIO,
                isGranted = check(Manifest.permission.RECORD_AUDIO),
                explanationEn = "Required only when you tap the mic or speak 'Hey Papu' commands in Tamil or English.",
                explanationTa = "தமிழ் மற்றும் ஆங்கில குரல் கட்டளைகளைக் கேட்க மைக்ரோஃபோன் அனுமதி தேவை."
            ),
            PermissionAuditItem(
                title = "Caller ID & Call Screening Role",
                permissionKey = "ROLE_CALL_SCREENING",
                isGranted = hasCallScreeningRole,
                explanationEn = "Identifies incoming callers via Android's official CallScreeningService and announces them before asking your permission to answer.",
                explanationTa = "அழைப்பவரை அடையாளம் கண்டு, உங்கள் அனுமதியுடன் அழைப்பை ஏற்க இது உதவுகிறது.",
                isSpecialSetting = true
            ),
            PermissionAuditItem(
                title = "Answer / End Calls (User-Approved Only)",
                permissionKey = Manifest.permission.ANSWER_PHONE_CALLS,
                isGranted = check(Manifest.permission.ANSWER_PHONE_CALLS),
                explanationEn = "Used strictly after you say 'Yes' or tap Approve to answer or decline a call.",
                explanationTa = "நீங்கள் 'ஆம்' என்று அனுமதி அளித்த பின்னரே அழைப்பை ஏற்க அல்லது துண்டிக்கப் பயன்படும்."
            ),
            PermissionAuditItem(
                title = "Contacts (Known vs Unknown Check)",
                permissionKey = Manifest.permission.READ_CONTACTS,
                isGranted = check(Manifest.permission.READ_CONTACTS),
                explanationEn = "Checks whether an incoming caller is saved in your contacts so Hey Papu never guesses identities.",
                explanationTa = "அழைப்பவர் உங்கள் தொடர்புகளில் உள்ளாரா என சரிபார்க்க மட்டுமே பயன்படும்."
            ),
            PermissionAuditItem(
                title = "Notification Listener (WhatsApp, IG, FB)",
                permissionKey = "NOTIFICATION_LISTENER",
                isGranted = hasNotificationAccess,
                explanationEn = "Reads incoming WhatsApp, WhatsApp Business, Instagram & Facebook notifications aloud and prepares draft replies with your approval.",
                explanationTa = "வாட்ஸ்அப், இன்ஸ்டாகிராம், ஃபேஸ்புக் அறிவிப்புகளைப் படித்து பதில்களைத் தயாரிக்க உதவும்.",
                isSpecialSetting = true
            ),
            PermissionAuditItem(
                title = "Calendar & Reminders",
                permissionKey = Manifest.permission.WRITE_CALENDAR,
                isGranted = check(Manifest.permission.WRITE_CALENDAR),
                explanationEn = "Schedules interviews, birthdays, events, and daily personal tasks in your calendar.",
                explanationTa = "நேர்காணல்கள், பிறந்தநாள் மற்றும் தினசரி நிகழ்வுகளை காலெண்டரில் சேர்க்க உதவும்."
            )
        )
    }
}
