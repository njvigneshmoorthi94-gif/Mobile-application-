package com.heypapu.ai.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiAgentClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    suspend fun generateResponse(
        apiKey: String,
        systemInstruction: String,
        userPrompt: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext localIntelligentFallback(userPrompt)
        }
        try {
            val url =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val payload = JSONObject().apply {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                })
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.5)
                    put("maxOutputTokens", 1200)
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext "AI Cloud Note (HTTP ${response.code}): Using built-in assistant mode.\n\n" +
                        localIntelligentFallback(userPrompt)
                }
                val json = JSONObject(body)
                val candidates = json.optJSONArray("candidates")
                val text = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                if (!text.isNullOrBlank()) text.trim() else localIntelligentFallback(userPrompt)
            }
        } catch (e: Exception) {
            localIntelligentFallback(userPrompt)
        }
    }

    private fun localIntelligentFallback(prompt: String): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("tamil") || prompt.any { it in '\u0B80'..'\u0BFF' } ->
                "வணக்கம்! நான் உங்கள் Hey Papu AI உதவியாளர். உங்கள் கட்டளை பெறப்பட்டது. முழு AI திறனுக்கு Security பகுதியில் Gemini API Key சேர்க்கவும்; தற்போது உள்ளமைக்கப்பட்ட குரல், வேலை தேடல், செய்திகள் மற்றும் நினைவூட்டல் கருவிகள் தயாராக உள்ளன."
            lower.contains("cover letter") || lower.contains("resume") ->
                "Tailored ATS Summary: Full-Stack Web & React Developer with hands-on expertise in React, WordPress CMS architecture, REST APIs, and AI-powered web platforms. Ready for review before your approval."
            else ->
                "Hey Papu AI is active! Add your free Google AI Studio Gemini API Key in the Security tab for deep generative replies, or use all built-in Job Search, Call Assistant, WhatsApp/Social Drafts, Live World News, and Reminder tools right now."
        }
    }
}
