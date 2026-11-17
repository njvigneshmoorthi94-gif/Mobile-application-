package com.heypapu.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heypapu.ai.ai.GeminiAgentClient
import com.heypapu.ai.calls.CallAnnouncementAndControlManager
import com.heypapu.ai.jobs.JobListing
import com.heypapu.ai.jobs.JobPortalRepository
import com.heypapu.ai.jobs.TrackedApplication
import com.heypapu.ai.news.NewsArticle
import com.heypapu.ai.news.WorldwideNewsRepository
import com.heypapu.ai.personal.PersonalAssistantManager
import com.heypapu.ai.personal.PersonalItem
import com.heypapu.ai.security.ActionCategory
import com.heypapu.ai.security.ApprovalGateManager
import com.heypapu.ai.security.PendingApprovalAction
import com.heypapu.ai.security.SecurityVault
import com.heypapu.ai.social.CapturedSocialNotification
import com.heypapu.ai.social.PapuNotificationListener
import com.heypapu.ai.social.SocialAssistantManager
import com.heypapu.ai.voice.BilingualVoiceEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val sender: String,
    val text: String,
    val isTamil: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val vault = SecurityVault(application)
    val voiceEngine = BilingualVoiceEngine(application)
    private val geminiClient = GeminiAgentClient()
    val jobRepo = JobPortalRepository(application)
    private val newsRepo = WorldwideNewsRepository()
    val personalManager = PersonalAssistantManager(application)

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _chatMessages = MutableStateFlow(
        listOf(
            ChatMessage(
                "Hey Papu AI",
                "Vanakkam ${vault.fullName}! I am Hey Papu AI — ready in Tamil & English. Tap the mic or say 'Hey Papu, find developer jobs' or 'Hey Papu, tell me today's world news'."
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _jobs = MutableStateFlow<List<JobListing>>(emptyList())
    val jobs: StateFlow<List<JobListing>> = _jobs.asStateFlow()

    private val _trackedApps = MutableStateFlow<List<TrackedApplication>>(emptyList())
    val trackedApps: StateFlow<List<TrackedApplication>> = _trackedApps.asStateFlow()

    private val _selectedJobForDraft = MutableStateFlow<JobListing?>(null)
    val selectedJobForDraft: StateFlow<JobListing?> = _selectedJobForDraft.asStateFlow()

    private val _draftedCoverLetter = MutableStateFlow("")
    val draftedCoverLetter: StateFlow<String> = _draftedCoverLetter.asStateFlow()

    private val _newsCategory = MutableStateFlow("India")
    val newsCategory: StateFlow<String> = _newsCategory.asStateFlow()

    private val _newsArticles = MutableStateFlow<List<NewsArticle>>(emptyList())
    val newsArticles: StateFlow<List<NewsArticle>> = _newsArticles.asStateFlow()

    private val _personalItems = MutableStateFlow<List<PersonalItem>>(emptyList())
    val personalItems: StateFlow<List<PersonalItem>> = _personalItems.asStateFlow()

    private val _weatherSummary = MutableStateFlow("Loading Tamil Nadu weather...")
    val weatherSummary: StateFlow<String> = _weatherSummary.asStateFlow()

    private val _socialDraftOutput = MutableStateFlow("")
    val socialDraftOutput: StateFlow<String> = _socialDraftOutput.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        CallAnnouncementAndControlManager.voiceEngineRef = voiceEngine
        voiceEngine.setRecognitionLanguage(vault.preferredLanguage)
        _trackedApps.value = jobRepo.getTrackedApplications()
        _personalItems.value = personalManager.getItems()
        refreshJobs("React WordPress Web AI Developer")
        refreshNews("India")
        refreshWeather()
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun processCommandOrChat(rawInput: String, speakReply: Boolean = true) {
        val input = rawInput.trim()
        if (input.isBlank()) return
        val isTamil = input.any { it in '\u0B80'..'\u0BFF' }
        appendChat("User", input, isTamil)

        val cleaned = input
            .replace(Regex("^(hey\s*papu[,\s]*|ஹே\s*பாப்பு[,\s]*)", RegexOption.IGNORE_CASE), "")
            .trim()
        val lower = cleaned.lowercase()

        when {
            lower in listOf("yes", "approve", "confirm", "answer", "answer this call", "ஆம்", "சரி") &&
                ApprovalGateManager.pendingAction.value != null -> {
                ApprovalGateManager.approveCurrent()
                replyWithSpeech("Confirmed and executed with your permission.", speakReply)
            }

            lower in listOf("no", "reject", "cancel", "decline", "இல்லை", "வேண்டாம்") &&
                ApprovalGateManager.pendingAction.value != null -> {
                ApprovalGateManager.rejectCurrent()
                replyWithSpeech("Cancelled as requested.", speakReply)
            }

            lower.contains("open whatsapp business") -> {
                val res = SocialAssistantManager.openApp(getApplication(), "com.whatsapp.w4b", "WhatsApp Business")
                replyWithSpeech(res, speakReply)
            }

            lower.contains("open whatsapp") || lower.contains("வாட்ஸ்அப் திற") -> {
                val res = SocialAssistantManager.openApp(getApplication(), "com.whatsapp", "WhatsApp")
                replyWithSpeech(res, speakReply)
            }

            lower.contains("open instagram") || lower.contains("இன்ஸ்டாகிராம்") -> {
                val res = SocialAssistantManager.openApp(getApplication(), "com.instagram.android", "Instagram")
                replyWithSpeech(res, speakReply)
            }

            lower.contains("open facebook") || lower.contains("ஃபேஸ்புக்") -> {
                val res = SocialAssistantManager.openApp(getApplication(), "com.facebook.katana", "Facebook")
                replyWithSpeech(res, speakReply)
            }

            lower.contains("find developer jobs") || lower.contains("find jobs") || lower.contains("வேலை") -> {
                _selectedTab.value = 1
                refreshJobs("React WordPress Web AI Developer")
                replyWithSpeech(
                    "Searching LinkedIn, Naukri, Indeed and global portals for WordPress, React, Web and AI Developer jobs matching your profile.",
                    speakReply
                )
            }

            lower.contains("apply for this job") -> {
                _selectedTab.value = 1
                val firstJob = _selectedJobForDraft.value ?: _jobs.value.firstOrNull()
                if (firstJob != null) {
                    prepareJobApplicationWithApproval(firstJob)
                    replyWithSpeech(
                        "I have prepared your tailored resume and cover letter for ${firstJob.title} at ${firstJob.company}. Please review and approve before I submit.",
                        speakReply
                    )
                } else {
                    replyWithSpeech("Please select a job listing first so I can prepare your application.", speakReply)
                }
            }

            lower.contains("who is calling") || lower.contains("யார் அழைக்கிறார்கள்") -> {
                _selectedTab.value = 2
                val call = CallAnnouncementAndControlManager.activeCall.value
                val msg = if (call != null) {
                    "Sir, ${call.displayIdentity} is calling. ${call.spamWarningReason} Shall I answer this call?"
                } else {
                    "There is no active incoming call right now, Sir."
                }
                replyWithSpeech(msg, speakReply)
            }

            lower.contains("answer this call") -> {
                val msg = CallAnnouncementAndControlManager.answerCallWithPermission(getApplication())
                replyWithSpeech(msg, speakReply)
            }

            lower.contains("world news") || lower.contains("today's news") || lower.contains("செய்தி") -> {
                _selectedTab.value = 3
                refreshNews("International")
                replyWithSpeech("Fetching top 10 verified international and Indian news headlines for you.", speakReply)
            }

            lower.contains("read my messages") || lower.contains("செய்திகளை படி") -> {
                _selectedTab.value = 2
                val latest = PapuNotificationListener.notifications.value.firstOrNull()
                val msg = if (latest != null) {
                    "Latest ${latest.appName} message from ${latest.sender}: ${latest.message}"
                } else {
                    "No unread WhatsApp or social notifications captured yet. Ensure Notification Access is enabled in Security settings."
                }
                replyWithSpeech(msg, speakReply)
            }

            lower.contains("remind me tomorrow") || lower.contains("நினைவூட்டு") -> {
                _selectedTab.value = 4
                val tomorrowMillis = System.currentTimeMillis() + 24 * 3600_000L
                val item = PersonalItem(
                    id = UUID.randomUUID().toString(),
                    type = "Voice Alarm",
                    title = cleaned,
                    dateTimeLabel = "Tomorrow (24 hrs)",
                    triggerAtMillis = tomorrowMillis
                )
                personalManager.addItemAndScheduleAlarm(item)
                _personalItems.value = personalManager.getItems()
                replyWithSpeech("Done! I have scheduled a voice reminder for tomorrow: $cleaned", speakReply)
            }

            else -> {
                viewModelScope.launch {
                    _isLoading.value = true
                    val sysPrompt =
                        "You are Hey Papu AI, a personal Android assistant for ${vault.fullName}. Respond concisely and helpfully in the same language the user used (Tamil or English). Never claim to perform external actions without user approval."
                    val answer = geminiClient.generateResponse(vault.geminiApiKey, sysPrompt, cleaned)
                    _isLoading.value = false
                    replyWithSpeech(answer, speakReply)
                }
            }
        }
    }

    private fun replyWithSpeech(text: String, speakReply: Boolean) {
        val isTamil = text.any { it in '\u0B80'..'\u0BFF' }
        appendChat("Hey Papu AI", text, isTamil)
        if (speakReply) {
            voiceEngine.speak(text)
        }
    }

    private fun appendChat(sender: String, text: String, isTamil: Boolean = false) {
        _chatMessages.value = _chatMessages.value + ChatMessage(sender, text, isTamil)
    }

    fun refreshJobs(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _jobs.value = jobRepo.searchWorldwideJobs(query, vault.skills)
            _isLoading.value = false
        }
    }

    fun prepareJobApplicationWithApproval(job: JobListing) {
        _selectedJobForDraft.value = job
        val (coverLetter, resumeFile) = jobRepo.generateTailoredResumeAndCoverLetter(
            candidateName = vault.fullName,
            candidateEmail = vault.email,
            skills = vault.skills,
            experience = vault.experienceSummary,
            job = job
        )
        _draftedCoverLetter.value = coverLetter

        ApprovalGateManager.requestApproval(
            PendingApprovalAction(
                category = ActionCategory.SUBMIT_JOB_APPLICATION,
                title = "Approve Job Application: ${job.title}",
                tamilPrompt = "${job.company} நிறுவனத்தின் ${job.title} பணிக்கு விண்ணப்பிக்க அனுமதிக்கிறீர்களா?",
                englishPrompt = "Sir, your resume and cover letter for ${job.title} at ${job.company} are ready. Shall I proceed to submit?",
                payloadPreview = "Portal: ${job.portal} | Match: ${job.matchScore}%\n\n${coverLetter.take(260)}...",
                onApproved = {
                    jobRepo.launchApprovedJobApplication(job, coverLetter, resumeFile)
                    _trackedApps.value = jobRepo.getTrackedApplications()
                }
            )
        )
    }

    fun draftSocialContent(platform: String, language: String, topic: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val prompt =
                "Write a high-engagement $platform post/caption/reply in $language about: $topic. Keep it authentic and ready to publish."
            val draft = geminiClient.generateResponse(
                vault.geminiApiKey,
                "You are a bilingual Tamil and English social media assistant.",
                prompt
            )
            _socialDraftOutput.value = draft
            _isLoading.value = false
        }
    }

    fun requestApprovedSocialShare(packageName: String, appLabel: String, content: String) {
        if (content.isBlank()) return
        ApprovalGateManager.requestApproval(
            PendingApprovalAction(
                category = ActionCategory.SHARE_INSTAGRAM_CONTENT,
                title = "Approve Publishing to $appLabel",
                tamilPrompt = "$appLabel-இல் இந்த பதிவை வெளியிட அனுமதிக்கிறீர்களா?",
                englishPrompt = "Shall I open $appLabel with this approved draft ready to post?",
                payloadPreview = content,
                onApproved = {
                    SocialAssistantManager.shareApprovedTextToApp(getApplication(), packageName, appLabel, content)
                }
            )
        )
    }

    fun requestApprovedWhatsAppReply(notif: CapturedSocialNotification, replyText: String) {
        ApprovalGateManager.requestApproval(
            PendingApprovalAction(
                category = ActionCategory.SEND_WHATSAPP_REPLY,
                title = "Approve ${notif.appName} Reply to ${notif.sender}",
                tamilPrompt = "${notif.sender} என்பவருக்கு பதில் அனுப்ப அனுமதிக்கிறீர்களா?",
                englishPrompt = "Shall I send this reply to ${notif.sender} on ${notif.appName}?",
                payloadPreview = "To: ${notif.sender}\nReply: $replyText",
                onApproved = {
                    SocialAssistantManager.sendApprovedDirectReply(getApplication(), notif, replyText)
                }
            )
        )
    }

    fun refreshNews(category: String) {
        _newsCategory.value = category
        viewModelScope.launch {
            _isLoading.value = true
            _newsArticles.value = newsRepo.fetchTopHeadlines(category)
            _isLoading.value = false
        }
    }

    fun readTopNewsAloud(inTamil: Boolean) {
        val list = _newsArticles.value.take(5)
        if (list.isEmpty()) return
        val summary = list.mapIndexed { idx, a ->
            val tag = if (a.isVerifiedMajorOutlet) "Verified" else "Developing"
            "Headline ${idx + 1}, $tag from ${a.source}: ${a.title}."
        }.joinToString(" ")

        if (inTamil) {
            viewModelScope.launch {
                val translated = geminiClient.generateResponse(
                    vault.geminiApiKey,
                    "Translate these news headlines clearly into natural spoken Tamil.",
                    summary
                )
                voiceEngine.speak(translated, "ta-IN")
            }
        } else {
            voiceEngine.speak(summary, "en-IN")
        }
    }

    fun addPersonalReminder(type: String, title: String, hoursFromNow: Int) {
        val trigger = System.currentTimeMillis() + (hoursFromNow * 3600_000L)
        val item = PersonalItem(
            id = UUID.randomUUID().toString(),
            type = type,
            title = title,
            dateTimeLabel = "In $hoursFromNow hr(s)",
            triggerAtMillis = trigger
        )
        personalManager.addItemAndScheduleAlarm(item)
        personalManager.insertIntoAndroidCalendar(title, trigger)
        _personalItems.value = personalManager.getItems()
    }

    fun refreshWeather() {
        viewModelScope.launch {
            _weatherSummary.value = personalManager.fetchLiveWeather()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.shutdown()
    }
}
