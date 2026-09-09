package com.celmatech.myjournalplus.ui.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.celmatech.myjournalplus.data.model.MoodType
import com.celmatech.myjournalplus.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodScreen(
    isPremium: Boolean,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: MoodViewModel = viewModel()
) {
    val logs by viewModel.logs.collectAsState()
    var selectedMood by remember { mutableStateOf<MoodType?>(null) }
    var intensity by remember { mutableFloatStateOf(3f) }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val avg = remember(logs) {
        if (logs.isEmpty()) 0.0 else logs.map { it.score }.average()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mood Tracker", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Free · Track patterns over time", fontSize = 11.sp, color = Color.White.copy(0.85f))
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
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFF7B6CFF), Color(0xFFA78BFA))),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(18.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("This week", color = Color.White.copy(0.9f), fontSize = 13.sp)
                                Text(
                                    if (logs.isEmpty()) "—" else String.format("%.1f / 5", avg),
                                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp
                                )
                                Text("${logs.size} logs", color = Color.White.copy(0.85f), fontSize = 12.sp)
                            }
                            Icon(Icons.Default.Insights, null, tint = Color.White.copy(0.9f), modifier = Modifier.size(40.dp))
                        }
                    }
                }
            }

            item {
                Text("How are you feeling?", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary, fontSize = 18.sp)
                Text("Choose one mood below", color = AppColors.TextSecondary, fontSize = 13.sp)
            }

            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false
                ) {
                    items(MoodType.entries.toList()) { mood ->
                        val selected = selectedMood == mood
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) AppColors.Primary.copy(0.15f) else Color.White)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) AppColors.Primary else AppColors.Border,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedMood = mood }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(mood.emoji, fontSize = 28.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                mood.label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) AppColors.Primary else AppColors.TextPrimary,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            item {
                Text("Intensity: ${intensity.toInt()}/5", color = AppColors.TextPrimary, fontWeight = FontWeight.Medium)
                Slider(
                    value = intensity,
                    onValueChange = { intensity = it },
                    valueRange = 1f..5f,
                    steps = 3,
                    colors = SliderDefaults.colors(thumbColor = AppColors.Primary, activeTrackColor = AppColors.Primary)
                )
            }

            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Optional note") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary,
                        focusedContainerColor = AppColors.FieldBg,
                        unfocusedContainerColor = AppColors.FieldBg,
                        focusedBorderColor = AppColors.Primary
                    ),
                    textStyle = LocalTextStyle.current.copy(color = AppColors.TextPrimary)
                )
            }

            item {
                Button(
                    onClick = {
                        selectedMood?.let { m ->
                            saving = true
                            viewModel.logMood(m.label, intensity.toInt(), note) {
                                saving = false
                                selectedMood = null
                                note = ""
                                intensity = 3f
                                message = "Mood saved"
                            }
                        }
                    },
                    enabled = selectedMood != null && !saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) {
                    Text(if (saving) "Saving…" else "Save mood", fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                if (message != null) {
                    Text(message!!, color = AppColors.Success, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }

            item {
                Text("Recent history", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary, fontSize = 16.sp)
            }

            if (logs.isEmpty()) {
                item {
                    Text("No moods yet — log your first above.", color = AppColors.TextMuted, fontSize = 13.sp)
                }
            } else {
                items(logs) { log ->
                    val emoji = MoodType.entries.find { it.label.equals(log.mood, true) }?.emoji ?: "·"
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(AppColors.Primary.copy(0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 22.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${log.mood} · ${log.score}/5", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                                if (log.note.isNotBlank()) {
                                    Text(log.note, color = AppColors.TextSecondary, fontSize = 13.sp, maxLines = 2)
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
                        }
                    }
                }
            }
        }
    }
}
