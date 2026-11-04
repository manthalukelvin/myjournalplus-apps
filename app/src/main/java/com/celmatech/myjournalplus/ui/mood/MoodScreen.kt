package com.celmatech.myjournalplus.ui.mood

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.celmatech.myjournalplus.data.model.MoodLog
import com.celmatech.myjournalplus.data.model.MoodType
import com.celmatech.myjournalplus.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.*

private val MoodColors = mapOf(
    "Happy" to Color(0xFFFBBF24),
    "Grateful" to Color(0xFF34D399),
    "Calm" to Color(0xFF60A5FA),
    "Neutral" to Color(0xFF9CA3AF),
    "Anxious" to Color(0xFFF472B6),
    "Sad" to Color(0xFF818CF8),
    "Angry" to Color(0xFFF87171),
    "Tired" to Color(0xFFA78BFA)
)

private fun moodColor(label: String): Color =
    MoodColors[label] ?: AppColors.Primary

private fun scoreLabel(score: Int): String = when (score) {
    1 -> "Very low"
    2 -> "Low"
    3 -> "Moderate"
    4 -> "Good"
    5 -> "Great"
    else -> "$score/5"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodScreen(
    isPremium: Boolean,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: MoodViewModel = viewModel()
) {
    val logs by viewModel.logs.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.error.collectAsState()

    var selectedMood by remember { mutableStateOf<MoodType?>(null) }
    var intensity by remember { mutableFloatStateOf(3f) }
    var note by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<MoodLog?>(null) }
    var deleting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mood Tracker", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "How are you feeling today?",
                            fontSize = 12.sp,
                            color = Color.White.copy(0.88f)
                        )
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
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Hero / today summary ──────────────────────────────────────
            item {
                HeroCard(stats = stats, logs = logs)
            }

            // ── Quick check-in ────────────────────────────────────────────
            item {
                Text(
                    "Check in",
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp
                )
                Text(
                    "Tap a mood, set intensity, optionally add a note",
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp
                )
            }

            item {
                MoodPickerGrid(
                    selected = selectedMood,
                    onSelect = { selectedMood = it }
                )
            }

            item {
                AnimatedVisibility(visible = selectedMood != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Intensity · ${intensity.toInt()}/5 · ${scoreLabel(intensity.toInt())}",
                            fontWeight = FontWeight.Medium,
                            color = AppColors.TextPrimary
                        )
                        Slider(
                            value = intensity,
                            onValueChange = { intensity = it },
                            valueRange = 1f..5f,
                            steps = 3,
                            colors = SliderDefaults.colors(
                                thumbColor = selectedMood?.let { moodColor(it.label) } ?: AppColors.Primary,
                                activeTrackColor = selectedMood?.let { moodColor(it.label) } ?: AppColors.Primary
                            )
                        )
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it.take(280) },
                            label = { Text("Optional note") },
                            placeholder = { Text("What's on your mind?") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.Primary,
                                unfocusedBorderColor = AppColors.Border,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                        Button(
                            onClick = {
                                val mood = selectedMood ?: return@Button
                                viewModel.logMood(mood, intensity.toInt(), note) { ok ->
                                    if (ok) {
                                        selectedMood = null
                                        intensity = 3f
                                        note = ""
                                        showSuccess = true
                                    }
                                }
                            },
                            enabled = selectedMood != null && !saving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Save mood", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            // Success toast
            item {
                AnimatedVisibility(visible = showSuccess, enter = fadeIn() + scaleIn(), exit = fadeOut()) {
                    LaunchedEffect(showSuccess) {
                        if (showSuccess) {
                            kotlinx.coroutines.delay(2200)
                            showSuccess = false
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AppColors.Success.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = AppColors.Success)
                            Spacer(Modifier.width(10.dp))
                            Text("Mood saved. Keep tracking!", color = AppColors.Success, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            if (error != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AppColors.Error.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.clearError() }
                    ) {
                        Text(
                            error!!,
                            color = AppColors.Error,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // ── Weekly overview + chart ───────────────────────────────────
            item {
                Text(
                    "This week",
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp
                )
            }

            item {
                WeekOverviewCard(stats = stats)
            }

            // ── Distribution ──────────────────────────────────────────────
            if (stats.distribution.isNotEmpty()) {
                item {
                    Text(
                        "Mood mix",
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary,
                        fontSize = 18.sp
                    )
                }
                item {
                    DistributionCard(distribution = stats.distribution, total = stats.totalLogs)
                }
            }

            // ── History ───────────────────────────────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "History",
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary,
                        fontSize = 18.sp
                    )
                    Text(
                        "${logs.size} entries",
                        color = AppColors.TextMuted,
                        fontSize = 13.sp
                    )
                }
            }

            if (logs.isEmpty()) {
                item {
                    EmptyMoodState()
                }
            } else {
                items(logs, key = { it.id }) { log ->
                    MoodHistoryRow(
                        log = log,
                        onDelete = { deleteTarget = log }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // Delete confirmation
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deleteTarget = null },
            title = { Text("Delete mood entry?") },
            text = {
                Text("This will permanently remove your ${target.mood} check-in.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleting = true
                        viewModel.deleteMood(target.id) { ok ->
                            deleting = false
                            if (ok) deleteTarget = null
                        }
                    },
                    enabled = !deleting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (deleting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }, enabled = !deleting) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HeroCard(stats: MoodStats, logs: List<MoodLog>) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF7B6CFF), Color(0xFF9F7AEA), Color(0xFFB794F4))
                    ),
                    RoundedCornerShape(22.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (stats.todayMood != null) "Today's mood" else "No check-in yet today",
                            color = Color.White.copy(0.9f),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        if (stats.todayMood != null) {
                            val emoji = MoodType.entries.find {
                                it.label.equals(stats.todayMood.mood, true)
                            }?.emoji ?: "·"
                            Text(
                                "$emoji  ${stats.todayMood.mood}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp
                            )
                            Text(
                                "${stats.todayMood.score}/5 · ${scoreLabel(stats.todayMood.score)}",
                                color = Color.White.copy(0.9f),
                                fontSize = 14.sp
                            )
                        } else {
                            Text(
                                "Tap a mood below",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 22.sp
                            )
                        }
                    }
                    if (stats.streakDays > 0) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(0.2f)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    null,
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "${stats.streakDays}d",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniStat("Week avg", if (stats.weekLogs.isEmpty()) "—" else String.format("%.1f", stats.weekAverage))
                    MiniStat("Month avg", if (stats.monthLogs.isEmpty()) "—" else String.format("%.1f", stats.monthAverage))
                    MiniStat("Total", "${stats.totalLogs}")
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(
        Modifier
            .background(Color.White.copy(0.18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = Color.White.copy(0.85f), fontSize = 11.sp)
    }
}

@Composable
private fun MoodPickerGrid(
    selected: MoodType?,
    onSelect: (MoodType) -> Unit
) {
    val moods = MoodType.entries
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        moods.chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { mood ->
                    val isSelected = selected == mood
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.06f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "moodScale"
                    )
                    val accent = moodColor(mood.label)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .scale(scale)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) accent.copy(0.15f) else Color.White)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) accent else AppColors.Border,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onSelect(mood) }
                            .padding(vertical = 14.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(mood.emoji, fontSize = 30.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            mood.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) accent else AppColors.TextPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // fill incomplete last row
                repeat(4 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WeekOverviewCard(stats: MoodStats) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("7-day trend", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                if (stats.weekLogs.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TrendingUp, null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            String.format("%.1f avg", stats.weekAverage),
                            color = AppColors.Primary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (stats.weekLogs.isEmpty()) {
                Text(
                    "Log a few moods this week to see your trend.",
                    color = AppColors.TextMuted,
                    fontSize = 13.sp
                )
            } else {
                WeekBarChart(logs = stats.weekLogs)
            }
        }
    }
}

@Composable
private fun WeekBarChart(logs: List<MoodLog>) {
    // Aggregate average score per weekday (Mon–Sun of current week)
    val cal = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY }
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val averages = FloatArray(7) { 0f }
    val counts = IntArray(7)

    logs.forEach { log ->
        val d = log.createdAt?.toDate() ?: return@forEach
        cal.time = d
        val idx = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0
        averages[idx] += log.score.toFloat()
        counts[idx]++
    }
    for (i in 0..6) {
        if (counts[i] > 0) averages[i] /= counts[i]
    }

    val maxBar = 5f
    Row(
        Modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        dayLabels.forEachIndexed { i, label ->
            val value = averages[i]
            val hasData = counts[i] > 0
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                if (hasData) {
                    Text(
                        String.format("%.0f", value),
                        fontSize = 10.sp,
                        color = AppColors.TextMuted
                    )
                } else {
                    Spacer(Modifier.height(14.dp))
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(((value / maxBar) * 72f).coerceAtLeast(if (hasData) 8f else 4f).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(
                            if (hasData) AppColors.Primary.copy(alpha = 0.35f + (value / maxBar) * 0.65f)
                            else AppColors.Border.copy(alpha = 0.4f)
                        )
                )
                Spacer(Modifier.height(6.dp))
                Text(label, fontSize = 11.sp, color = AppColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun DistributionCard(distribution: Map<String, Int>, total: Int) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            distribution.entries.take(8).forEach { (mood, count) ->
                val fraction = if (total > 0) count.toFloat() / total else 0f
                val emoji = MoodType.entries.find { it.label.equals(mood, true) }?.emoji ?: "·"
                val color = moodColor(mood)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 18.sp, modifier = Modifier.width(28.dp))
                    Text(
                        mood,
                        color = AppColors.TextPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.width(72.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(AppColors.Border.copy(0.35f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                                .clip(RoundedCornerShape(5.dp))
                                .background(color.copy(alpha = 0.85f))
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$count",
                        color = AppColors.TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.width(24.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodHistoryRow(log: MoodLog, onDelete: () -> Unit) {
    val emoji = MoodType.entries.find { it.label.equals(log.mood, true) }?.emoji ?: "·"
    val accent = moodColor(log.mood)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accent.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${log.mood} · ${log.score}/5",
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextPrimary
                )
                if (log.note.isNotBlank()) {
                    Text(
                        log.note,
                        color = AppColors.TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                log.createdAt?.toDate()?.let {
                    SimpleDateFormat("MMM d\nh:mm a", Locale.getDefault()).format(it)
                } ?: "",
                color = AppColors.TextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.End
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, "Delete", tint = AppColors.Error, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EmptyMoodState() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Mood,
                null,
                tint = AppColors.Primary.copy(0.5f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No mood entries yet",
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Checking in regularly helps you notice patterns and celebrate progress.",
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
