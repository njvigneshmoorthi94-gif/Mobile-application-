package com.heypapu.ai.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurityVault(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "hey_papu_secure_vault",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isPinConfigured(): Boolean = !prefs.getString("user_pin", null).isNullOrEmpty()

    fun setPin(pin: String) {
        prefs.edit().putString("user_pin", pin).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val saved = prefs.getString("user_pin", null)
        return saved != null && saved == pin
    }

    var geminiApiKey: String
        get() = prefs.getString("gemini_api_key", "") ?: ""
        set(value) = prefs.edit().putString("gemini_api_key", value.trim()).apply()

    var fullName: String
        get() = prefs.getString("profile_name", "Vignesh Moorthi N") ?: "Vignesh Moorthi N"
        set(value) = prefs.edit().putString("profile_name", value.trim()).apply()

    var email: String
        get() = prefs.getString("profile_email", "") ?: ""
        set(value) = prefs.edit().putString("profile_email", value.trim()).apply()

    var skills: String
        get() = prefs.getString(
            "profile_skills",
            "React, WordPress, Full-Stack Web Development, JavaScript, TypeScript, Node.js, HTML5, CSS3, REST APIs, AI Integration, ATS Resume Optimization"
        ) ?: ""
        set(value) = prefs.edit().putString("profile_skills", value.trim()).apply()

    var experienceSummary: String
        get() = prefs.getString(
            "profile_experience",
            "Full-Stack Web & React Developer specializing in custom WordPress themes/plugins, modern React frontends, CMS architecture, and AI-powered web portals."
        ) ?: ""
        set(value) = prefs.edit().putString("profile_experience", value.trim()).apply()

    var preferredLanguage: String
        get() = prefs.getString("preferred_lang", "en-IN") ?: "en-IN"
        set(value) = prefs.edit().putString("preferred_lang", value).apply()

    var speakIncomingMessages: Boolean
        get() = prefs.getBoolean("speak_incoming_msgs", true)
        set(value) = prefs.edit().putBoolean("speak_incoming_msgs", value).apply()
}
