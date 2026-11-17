package com.heypapu.ai

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heypapu.ai.calls.CallAnnouncementAndControlManager
import com.heypapu.ai.calls.IncomingCallInfo
import com.heypapu.ai.security.ApprovalGateManager
import com.heypapu.ai.security.PermissionAuditor
import com.heypapu.ai.social.PapuNotificationListener
import com.heypapu.ai.social.SocialAssistantManager

private val BgDark = Color(0xFF090D16)
private val CardDark = Color(0xFF131B2E)
private val AccentCyan = Color(0xFF00E5FF)
private val AccentEmerald = Color(0xFF10B981)
private val AccentAmber = Color(0xFFF59E0B)
private val AccentRed = Color(0xFFEF4444)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BgDark,
                    surface = CardDark,
                    primary = AccentCyan,
                    onPrimary = Color.Black
                )
            ) {
                HeyPapuRootScreen()
            }
        }
    }
}

@Composable
fun HeyPapuRootScreen(vm: MainViewModel = viewModel()) {
    var isUnlocked by remember { mutableStateOf(!vm.vault.isPinConfigured()) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    LaunchedEffect(Unit) {
        permLauncher.launch(PermissionAuditor.getRuntimePermissionsToRequest())
    }

    if (!isUnlocked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardDark)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Hey Papu AI Vault", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Enter your security PIN", fontSize = 13.sp, color = Color.LightGray)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it.take(6) },
                    label = { Text("Security PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                if (pinError.isNotEmpty()) {
                    Text(pinError, color = AccentRed, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (vm.vault.verifyPin(pinInput)) {
                            isUnlocked = true
                        } else {
                            pinError = "Incorrect PIN. Try again."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unlock Assistant")
                }
            }
        }
        return
    }

    val selectedTab by vm.selectedTab.collectAsState()
    val isListening by vm.voiceEngine.isListening.collectAsState()
    val activeLocale by vm.voiceEngine.activeLocaleTag.collectAsState()
    val pendingApproval by ApprovalGateManager.pendingAction.collectAsState()

    Scaffold(
        containerColor = BgDark,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardDark)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("HEY PAPU AI", color = AccentCyan, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("Tamil + English Personal Agent", color = Color.LightGray, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = {
                            val next = vm.voiceEngine.toggleLanguage()
                            vm.vault.preferredLanguage = next
                        },
                        label = {
                            Text(
                                if (activeLocale == "ta-IN") "தமிழ் (TA)" else "ENG (IN)",
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { vm.voiceEngine.stopSpeaking() }) {
                        Icon(Icons.Default.VolumeOff, contentDescription = "Mute TTS", tint = Color.LightGray)
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isListening) {
                        vm.voiceEngine.stopListening()
                    } else {
                        vm.voiceEngine.startListening { spoken ->
                            vm.processCommandOrChat(spoken, speakReply = true)
                        }
                    }
                },
                containerColor = if (isListening) AccentRed else AccentCyan,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                    contentDescription = "Hey Papu Voice Activation",
                    tint = Color.Black
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = CardDark) {
                val navItems = listOf(
                    Triple("Chat", Icons.Default.AutoAwesome, 0),
                    Triple("Jobs", Icons.Default.Work, 1),
                    Triple("Calls/Social", Icons.Default.PhoneInTalk, 2),
                    Triple("News", Icons.Default.Public, 3),
                    Triple("Personal", Icons.Default.EventAvailable, 4),
                    Triple("Security", Icons.Default.Security, 5)
                )
                navItems.forEach { (label, icon, idx) ->
                    NavigationBarItem(
                        selected = selectedTab == idx,
                        onClick = { vm.selectTab(idx) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            pendingApproval?.let { action ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .border(1.5.dp, AccentAmber, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF231B0E))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "PERMISSION REQUIRED • ${action.title}",
                            color = AccentAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(action.englishPrompt, color = Color.White, fontSize = 13.sp)
                        Text(action.tamilPrompt, color = Color(0xFFFDE68A), fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            action.payloadPreview,
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            maxLines = 4
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { ApprovalGateManager.approveCurrent() },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                            ) {
                                Text("YES • APPROVE", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = { ApprovalGateManager.rejectCurrent() }) {
                                Text("NO • DECLINE", color = AccentRed)
                            }
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> DashboardAndChatTab(vm)
                1 -> JobsAssistantTab(vm)
                2 -> CallsAndSocialTab(vm)
                3 -> WorldwideNewsTab(vm)
                4 -> PersonalAssistantTab(vm)
                5 -> SecurityAndPermissionsTab(vm)
            }
        }
    }
}

@Composable
fun DashboardAndChatTab(vm: MainViewModel) {
    val messages by vm.chatMessages.collectAsState()
    val weather by vm.weatherSummary.collectAsState()
    val recognized by vm.voiceEngine.recognizedText.collectAsState()
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Hello, ${vm.vault.fullName}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(weather, color = AccentCyan, fontSize = 12.sp)
                if (recognized.isNotBlank()) {
                    Text("Last Voice: \"$recognized\"", color = AccentEmerald, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Hey Papu, find developer jobs",
                        "Hey Papu, tell me today's world news",
                        "Hey Papu, open WhatsApp",
                        "Hey Papu, read my messages",
                        "Hey Papu, remind me tomorrow"
                    ).forEach { cmd ->
                        SuggestionChip(
                            onClick = { vm.processCommandOrChat(cmd, speakReply = true) },
                            label = { Text(cmd.removePrefix("Hey Papu, "), fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { msg ->
                val isUser = msg.sender == "User"
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isUser) Color(0xFF0E3A47) else CardDark)
                            .padding(12.dp)
                    ) {
                        Text(
                            msg.sender,
                            color = if (isUser) AccentCyan else AccentEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(msg.text, color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask in English or தமிழ்...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    vm.processCommandOrChat(input, speakReply = false)
                    input = ""
                },
                modifier = Modifier.background(AccentCyan, CircleShape)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
            }
        }
    }
}

@Composable
fun JobsAssistantTab(vm: MainViewModel) {
    val jobs by vm.jobs.collectAsState()
    val tracked by vm.trackedApps.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    var searchQuery by remember { mutableStateOf("React WordPress Web AI Developer") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("AI Job Application Assistant", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Matches your React, WordPress, Web & AI skills. Always asks permission before applying.", color = Color.LightGray, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Role / Skills") }
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.refreshJobs(searchQuery) }) {
                    Text("Search")
                }
            }
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }

        items(jobs) { job ->
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(job.portal, color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("${job.matchScore}% Skill Match", color = AccentEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(job.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${job.company} • ${job.location}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Matched: ${job.matchedSkills.joinToString(", ")}", color = Color(0xFF93C5FD), fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.prepareJobApplicationWithApproval(job) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Prepare Resume & Request Approval to Apply")
                    }
                }
            }
        }

        if (tracked.isNotEmpty()) {
            item {
                Text("Tracked Applications & Interviews (${tracked.size})", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            items(tracked) { app ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2236))) {
                    Column(modifier = Modifier.padding(10.dp).fillMaxWidth()) {
                        Text("${app.jobTitle} — ${app.company}", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Status: ${app.status} | Applied: ${app.appliedDate} | Interview: ${app.interviewDate}", color = Color.LightGray, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CallsAndSocialTab(vm: MainViewModel) {
    val context = LocalContext.current
    val activeCall by CallAnnouncementAndControlManager.activeCall.collectAsState()
    val speakerOn by CallAnnouncementAndControlManager.speakerphoneEnabled.collectAsState()
    val notifications by PapuNotificationListener.notifications.collectAsState()
    val socialDraft by vm.socialDraftOutput.collectAsState()
    var topicInput by remember { mutableStateOf("Launch of our new React & WordPress AI Web Portal") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("AI Phone Call Assistant", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        activeCall?.let { "Incoming: ${it.displayIdentity} (${it.spamWarningReason})" }
                            ?: "Monitoring permitted Caller ID via CallScreeningService. Never answers or records without approval.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            CallAnnouncementAndControlManager.onIncomingCallScreened(
                                context,
                                IncomingCallInfo(
                                    phoneNumber = "+91-98400-XXXXX",
                                    resolvedName = null,
                                    displayIdentity = "+91-98400-XXXXX",
                                    isKnownContact = false,
                                    spamRiskLevel = "UNKNOWN",
                                    spamWarningReason = "Unknown caller not in your contacts."
                                )
                            )
                        }) {
                            Text("Test Call Prompt", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = {
                            CallAnnouncementAndControlManager.toggleSpeakerphone(context)
                        }) {
                            Text(if (speakerOn) "Speaker: ON" else "Speaker: OFF", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        item {
            Text("Social & Messaging AI Hub", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("WhatsApp", "com.whatsapp", AccentEmerald),
                    Triple("WA Business", "com.whatsapp.w4b", AccentEmerald),
                    Triple("Instagram", "com.instagram.android", Color(0xFFE1306C)),
                    Triple("Facebook", "com.facebook.katana", Color(0xFF1877F2))
                ).forEach { (label, pkg, color) ->
                    Button(
                        onClick = { SocialAssistantManager.openApp(context, pkg, label) },
                        colors = ButtonDefaults.buttonColors(containerColor = color)
                    ) {
                        Text(label, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Draft Posts, Captions, Reels & Replies (Tamil / English)", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = topicInput,
                        onValueChange = { topicInput = it },
                        label = { Text("Topic or Customer Message") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.draftSocialContent("Instagram Reel & Post", "English", topicInput) }) {
                            Text("English Draft", fontSize = 11.sp)
                        }
                        Button(onClick = { vm.draftSocialContent("WhatsApp & Instagram", "Tamil (தமிழ்)", topicInput) }) {
                            Text("தமிழ் Draft", fontSize = 11.sp)
                        }
                    }
                    if (socialDraft.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(socialDraft, color = Color(0xFFE2E8F0), fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                vm.requestApprovedSocialShare("com.instagram.android", "Instagram", socialDraft)
                            }) {
                                Text("Share to Instagram (Approval)", fontSize = 11.sp)
                            }
                            OutlinedButton(onClick = {
                                vm.requestApprovedSocialShare("com.facebook.katana", "Facebook", socialDraft)
                            }) {
                                Text("Post to Facebook (Approval)", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Incoming Messages & Social Notifications (${notifications.size})", color = AccentAmber, fontWeight = FontWeight.Bold)
        }
        items(notifications) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("${item.appName} • ${item.sender}", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(item.message, color = Color.White, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            vm.requestApprovedWhatsAppReply(item, "Thank you for your message! I will get back to you shortly.")
                        }) {
                            Text("Approve English Reply", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = {
                            vm.requestApprovedWhatsAppReply(item, "உங்கள் செய்திக்கு நன்றி! விரைவில் தொடர்பு கொள்கிறேன்.")
                        }) {
                            Text("Approve தமிழ் Reply", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorldwideNewsTab(vm: MainViewModel) {
    val context = LocalContext.current
    val category by vm.newsCategory.collectAsState()
    val articles by vm.newsArticles.collectAsState()
    val categories = listOf("India", "Tamil Nadu", "Business", "Technology", "AI", "Sports", "International")

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Worldwide News AI (Top 10 Verified Feed)", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = { vm.refreshNews(cat) },
                    label = { Text(cat, fontSize = 11.sp) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            Button(onClick = { vm.readTopNewsAloud(inTamil = false) }) {
                Text("Read Aloud (English)", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { vm.readTopNewsAloud(inTamil = true) }) {
                Text("தமிழில் வாசி", fontSize = 12.sp)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(articles) { article ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.link)))
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (article.isVerifiedMajorOutlet) "VERIFIED OUTLET • ${article.source}" else "UNCONFIRMED / AGGREGATED • ${article.source}",
                                color = if (article.isVerifiedMajorOutlet) AccentEmerald else AccentAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(article.publishedDate, color = Color.Gray, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(article.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(article.link, color = AccentCyan, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun PersonalAssistantTab(vm: MainViewModel) {
    val items by vm.personalItems.collectAsState()
    val weather by vm.weatherSummary.collectAsState()
    var titleInput by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Birthday") }
    var hoursAhead by remember { mutableStateOf("2") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Personal AI Assistant", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(weather, color = AccentEmerald, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.personalManager.openGmailInbox() }) {
                            Text("Open Gmail", fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = { vm.refreshWeather() }) {
                            Text("Refresh Weather", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Add Birthday, Event, Task or Voice Alarm", color = Color.White, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Birthday", "Event", "Voice Alarm", "Task").forEach { t ->
                            FilterChip(selected = selectedType == t, onClick = { selectedType = t }, label = { Text(t) })
                        }
                    }
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Reminder / Event Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = hoursAhead,
                            onValueChange = { hoursAhead = it.filter { c -> c.isDigit() } },
                            label = { Text("Hours from now") },
                            modifier = Modifier.width(140.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Button(onClick = {
                            if (titleInput.isNotBlank()) {
                                vm.addPersonalReminder(selectedType, titleInput, hoursAhead.toIntOrNull() ?: 1)
                                titleInput = ""
                            }
                        }) {
                            Text("Save & Set Alarm")
                        }
                    }
                }
            }
        }

        items(items) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${item.type} • ${item.dateTimeLabel}", color = AccentCyan, fontSize = 11.sp)
                        Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Checkbox(
                        checked = item.isCompleted,
                        onCheckedChange = {
                            vm.personalManager.toggleTaskCompleted(item.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SecurityAndPermissionsTab(vm: MainViewModel) {
    val context = LocalContext.current
    val auditList = remember { PermissionAuditor.auditAll(context) }
    val auditLogs by ApprovalGateManager.auditLog.collectAsState()
    var apiKeyInput by remember { mutableStateOf(vm.vault.geminiApiKey) }
    var skillsInput by remember { mutableStateOf(vm.vault.skills) }
    var newPinInput by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("") }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Security, Vault & Permission Transparency", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("Zero-Action-Without-Approval Policy: Hey Papu AI never shares data, sends messages, answers calls, or applies for jobs without your confirmation.", color = Color.LightGray, fontSize = 12.sp)
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Encrypted Profile & AI Key Vault", color = Color.White, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Google Gemini API Key (Optional for Cloud AI)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = skillsInput,
                        onValueChange = { skillsInput = it },
                        label = { Text("Your Job Matching Skills") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { newPinInput = it.take(6) },
                        label = { Text("Set / Update App Lock PIN") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        vm.vault.geminiApiKey = apiKeyInput
                        vm.vault.skills = skillsInput
                        if (newPinInput.length >= 4) vm.vault.setPin(newPinInput)
                        statusMsg = "Encrypted Vault Saved!"
                    }) {
                        Text("Save Secure Settings")
                    }
                    if (statusMsg.isNotEmpty()) {
                        Text(statusMsg, color = AccentEmerald, fontSize = 12.sp)
                    }
                }
            }
        }

        items(auditList) { perm ->
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(perm.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            if (perm.isGranted) "GRANTED" else "ACTION NEEDED",
                            color = if (perm.isGranted) AccentEmerald else AccentAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(perm.explanationEn, color = Color.LightGray, fontSize = 11.sp)
                    Text(perm.explanationTa, color = Color(0xFF93C5FD), fontSize = 11.sp)
                    if (!perm.isGranted && perm.isSpecialSetting) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = {
                            if (perm.permissionKey == "ROLE_CALL_SCREENING") {
                                val rm = context.getSystemService(RoleManager::class.java)
                                val intent = rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                                roleLauncher.launch(intent)
                            } else {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        }) {
                            Text("Open Android Permission Setting", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        if (auditLogs.isNotEmpty()) {
            item {
                Text("Security Approval Audit Log", color = AccentAmber, fontWeight = FontWeight.Bold)
                auditLogs.take(10).forEach { log ->
                    Text(log, color = Color.LightGray, fontSize = 11.sp)
                }
            }
        }
    }
}
