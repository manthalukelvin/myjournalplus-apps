package com.celmatech.myjournalplus.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celmatech.myjournalplus.ui.components.PremiumBadge
import com.celmatech.myjournalplus.ui.components.PremiumUpgradeDialog
import com.celmatech.myjournalplus.ui.theme.AppColors
import com.celmatech.myjournalplus.util.GeminiService
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ChatMessage(
    val id: String = "",
    val role: String = "user", // user | assistant
    val text: String = "",
    val createdAt: Timestamp? = null
)

private val SuggestedQuestions = listOf(
    "What patterns do you see in my recent moods?",
    "Help me reflect on my last few journal entries.",
    "What's one small step I can take for my mental wellbeing today?",
    "How can I be kinder to myself based on what I wrote?",
    "Summarize how I've been feeling this week.",
    "I feel overwhelmed — what might help me reset?",
    "What strengths show up in my journaling?",
    "Suggest a gentle evening wind-down based on my entries."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiInsightsScreen(isPremium: Boolean, onBack: () -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var freeUsed by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }
    var loadingHistory by remember { mutableStateOf(true) }

    // Load free-usage flag + latest chat history
    LaunchedEffect(uid) {
        if (uid == null) {
            loadingHistory = false
            return@LaunchedEffect
        }
        try {
            val db = FirebaseFirestore.getInstance()
            val user = db.collection("users").document(uid).get().await()
            freeUsed = ((user.getLong("aiFreeUsedCount") ?: 0L) >= 1L) && !isPremium

            val chats = db.collection("users").document(uid).collection("aiChats")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
            val latest = chats.documents.firstOrNull()
            if (latest != null) {
                chatId = latest.id
                val msgs = latest.reference.collection("messages")
                    .orderBy("createdAt", Query.Direction.ASCENDING)
                    .limit(50)
                    .get()
                    .await()
                messages = msgs.documents.map { d ->
                    ChatMessage(
                        id = d.id,
                        role = d.getString("role") ?: "user",
                        text = d.getString("text") ?: "",
                        createdAt = d.getTimestamp("createdAt")
                    )
                }
            }
        } catch (_: Exception) {
        }
        loadingHistory = false
    }

    fun canAsk(): Boolean = isPremium || !freeUsed

    fun send(question: String) {
        val q = question.trim()
        if (q.isBlank() || loading || uid == null) return
        if (!canAsk()) {
            showPremiumDialog = true
            return
        }
        focus.clearFocus()
        scope.launch {
            loading = true
            error = null
            // Optimistic user bubble
            messages = messages + ChatMessage(id = "local_u_${System.currentTimeMillis()}", role = "user", text = q)
            input = ""
            try {
                listState.animateScrollToItem((messages.size).coerceAtLeast(0))
            } catch (_: Exception) { }

            GeminiService.ask(q, chatId).fold(
                onSuccess = { reply ->
                    chatId = reply.chatId.ifBlank { chatId }
                    messages = messages + ChatMessage(
                        id = "local_a_${System.currentTimeMillis()}",
                        role = "assistant",
                        text = reply.answer
                    )
                    if (!isPremium) freeUsed = true
                },
                onFailure = { e ->
                    if (e is GeminiService.FreeLimitException) {
                        freeUsed = true
                        showPremiumDialog = true
                        error = "You've used your free AI question. Upgrade to Premium for unlimited chat."
                    } else {
                        error = e.message
                        // Remove optimistic user message on hard failure? keep it so user sees what they asked
                    }
                }
            )
            loading = false
            try {
                listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0))
            } catch (_: Exception) { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AI Insights", color = Color.White, fontWeight = FontWeight.SemiBold)
                        if (isPremium) {
                            Spacer(Modifier.width(8.dp))
                            PremiumBadge()
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Primary)
            )
        },
        containerColor = AppColors.ScreenBg
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Free tier banner
            if (!isPremium) {
                Surface(
                    color = if (freeUsed) Color(0xFFFFF1F2) else Color(0xFFEEF2FF),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (freeUsed) Icons.Default.Lock else Icons.Default.AutoAwesome,
                            null,
                            tint = if (freeUsed) AppColors.Error else AppColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (freeUsed)
                                "Free AI used — upgrade for unlimited chat"
                            else
                                "1 free AI question · then Premium unlocks unlimited chat",
                            color = AppColors.TextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (freeUsed) {
                            TextButton(onClick = { showPremiumDialog = true }) {
                                Text("Upgrade", color = AppColors.Primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            if (loadingHistory) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Icon(Icons.Default.AutoAwesome, null, tint = AppColors.Primary)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Your AI journal companion",
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.TextPrimary
                                    )
                                    Text(
                                        "Ask anything about your entries or moods. Answers are grounded in what you've written.",
                                        color = AppColors.TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                        item {
                            Text(
                                "Try a question",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            )
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SuggestedQuestions.forEach { q ->
                                    SuggestionChip(
                                        text = q,
                                        enabled = canAsk() && !loading,
                                        onClick = { send(q) }
                                    )
                                }
                            }
                        }
                    } else {
                        items(messages, key = { it.id }) { msg ->
                            MessageBubble(msg)
                        }
                        if (loading) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color.White
                                    ) {
                                        Row(
                                            Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = AppColors.Primary
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text("Thinking…", color = AppColors.TextSecondary, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                        // Suggestions after first reply if premium or free still available
                        if (!loading && canAsk()) {
                            item {
                                Text(
                                    "Suggested follow-ups",
                                    fontWeight = FontWeight.Medium,
                                    color = AppColors.TextSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(SuggestedQuestions.take(4)) { q ->
                                        AssistChip(
                                            onClick = { send(q) },
                                            label = { Text(q.take(42) + if (q.length > 42) "…" else "", fontSize = 12.sp) },
                                            colors = AssistChipDefaults.assistChipColors(containerColor = Color.White)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (error != null) {
                        item {
                            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }
                    }
                }

                // Composer
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp,
                    color = Color.White
                ) {
                    Column(Modifier.padding(12.dp).navigationBarsPadding()) {
                        if (!canAsk()) {
                            Button(
                                onClick = { showPremiumDialog = true },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                            ) {
                                Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Unlock unlimited AI with Premium")
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { if (it.length <= 2000) input = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = {
                                        Text(
                                            if (isPremium) "Ask about your journal or mood…"
                                            else "Your free AI question…",
                                            fontSize = 14.sp
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    maxLines = 4,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { send(input) }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = AppColors.TextPrimary,
                                        unfocusedTextColor = AppColors.TextPrimary,
                                        focusedBorderColor = AppColors.Primary,
                                        unfocusedBorderColor = AppColors.Border,
                                        focusedContainerColor = AppColors.FieldBg,
                                        unfocusedContainerColor = AppColors.FieldBg
                                    )
                                )
                                FilledIconButton(
                                    onClick = { send(input) },
                                    enabled = !loading && input.isNotBlank(),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = AppColors.Primary
                                    )
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPremiumDialog) {
        PremiumUpgradeDialog(onDismiss = { showPremiumDialog = false })
    }
}

@Composable
private fun SuggestionChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            color = if (enabled) AppColors.TextPrimary else AppColors.TextMuted,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) AppColors.Primary else Color.White,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                msg.text,
                Modifier.padding(14.dp),
                color = if (isUser) Color.White else AppColors.TextPrimary,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}
