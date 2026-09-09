package com.celmatech.myjournalplus.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.celmatech.myjournalplus.data.model.JournalEntry
import com.celmatech.myjournalplus.data.model.UserProfile
import com.celmatech.myjournalplus.ui.components.PremiumBadge
import com.celmatech.myjournalplus.ui.components.AdBanner
import com.celmatech.myjournalplus.ui.components.PremiumUpgradeDialog
import com.celmatech.myjournalplus.ui.theme.Primary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    profile: UserProfile?,
    onNewEntry: () -> Unit,
    onEditEntry: (String) -> Unit,
    onMood: () -> Unit,
    onInsights: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val selected by viewModel.selectedEntry.collectAsState()
    var showPreview by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }

    val filtered = remember(entries, searchQuery) {
        if (searchQuery.isBlank()) entries
        else entries.filter {
            it.title.contains(searchQuery, true) || it.content.contains(searchQuery, true)
        }
    }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val displayName = profile?.displayName?.ifBlank { null } ?: "there"
    val totalWords = entries.sumOf { it.wordCount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "MyJournal+",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            if (profile?.isPremium == true) {
                                Spacer(Modifier.width(8.dp))
                                PremiumBadge()
                            }
                        }
                        Text(
                            "Reflect · Track · Grow",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Menu", tint = Color.White)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = { showMenu = false; onSettings() },
                            leadingIcon = { Icon(Icons.Default.Settings, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Logout") },
                            onClick = { showMenu = false; onLogout() },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewEntry,
                containerColor = Primary,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Edit, null) },
                text = { Text("New entry", fontWeight = FontWeight.SemiBold) }
            )
        },
        bottomBar = {
            Column {
                if (profile?.isPremium != true) {
                    AdBanner()
                }
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = true,
                        onClick = {},
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Home") },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Primary.copy(alpha = 0.15f), selectedIconColor = Primary)
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onMood,
                        icon = { Icon(Icons.Default.Mood, null) },
                        label = { Text("Mood") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onInsights,
                        icon = { Icon(Icons.Default.AutoAwesome, null) },
                        label = { Text("Insights") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onSettings,
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("Settings") }
                    )
                }
            }
        },
        containerColor = Color(0xFFF5F3FF)
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> {
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center),
                        color = Primary
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Greeting header
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFF7B6CFF), Color(0xFFA78BFA))
                                            ),
                                            RoundedCornerShape(20.dp)
                                        )
                                        .padding(20.dp)
                                ) {
                                    Column {
                                        Text(
                                            "$greeting, $displayName ✨",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Take a moment to reflect today.",
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 14.sp
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            StatChip("${entries.size}", "Entries")
                                            StatChip("$totalWords", "Words")
                                            if (profile?.isPremium == true) {
                                                StatChip("✦", "Premium")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Search
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search entries...") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, null)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = Primary
                                )
                            )
                        }

                        if (filtered.isEmpty()) {
                            item {
                                EmptyState(
                                    hasEntries = entries.isNotEmpty(),
                                    onNewEntry = onNewEntry
                                )
                            }
                        } else {
                            items(filtered, key = { it.id }) { entry ->
                                EntryCard(
                                    entry = entry,
                                    onClick = {
                                        viewModel.selectEntry(entry)
                                        showPreview = true
                                    }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(72.dp)) }
                    }
                }
            }

            if (showPreview && selected != null) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showPreview = false
                        viewModel.selectEntry(null)
                    },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = Color.White
                ) {
                    EntryPreviewContent(
                        entry = selected!!,
                        onEdit = {
                            showPreview = false
                            onEditEntry(selected!!.id)
                        },
                        onDelete = {
                            viewModel.deleteEntry(selected!!)
                            showPreview = false
                        }
                    )
                }
            }

            if (showPremiumDialog) {
                PremiumUpgradeDialog(onDismiss = { showPremiumDialog = false })
            }
        }
    }
}

@Composable
private fun StatChip(value: String, label: String) {
    Column(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
    }
}

@Composable
private fun EmptyState(hasEntries: Boolean, onNewEntry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.EditNote,
                null,
                modifier = Modifier.size(40.dp),
                tint = Primary
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (hasEntries) "No matching entries" else "Your journal is empty",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasEntries) "Try a different search"
            else "Write your first reflection and start\nbuilding a habit of mindfulness.",
            color = Color(0xFF6B7280),
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        if (!hasEntries) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onNewEntry,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Write first entry")
            }
        }
    }
}

@Composable
fun EntryCard(entry: JournalEntry, onClick: () -> Unit) {
    val dateStr = entry.createdAt?.toDate()?.let {
        SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(it)
    } ?: ""
    val preview = entry.content.trim().take(160).let {
        if (entry.content.length > 160) "$it…" else it
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entry.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF1F1B2E)
                )
                if (!entry.mood.isNullOrBlank()) {
                    Text(entry.mood ?: "", fontSize = 20.sp)
                }
            }
            if (preview.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateStr, fontSize = 12.sp, color = Color(0xFF9CA3AF))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.isFavorite) {
                        Icon(Icons.Default.Favorite, null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("${entry.wordCount} words", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                }
            }
        }
    }
}

@Composable
fun EntryPreviewContent(
    entry: JournalEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = entry.createdAt?.toDate()?.let {
        SimpleDateFormat("EEEE, MMM d, yyyy · h:mm a", Locale.getDefault()).format(it)
    } ?: ""
    val scroll = rememberScrollState()

    // Scrollable body + fixed Edit/Delete so long entries never hide the buttons
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
        ) {
            Text(
                entry.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F1B2E)
            )
            Spacer(Modifier.height(6.dp))
            Text(dateStr, color = Color(0xFF6B7280), fontSize = 13.sp)
            if (!entry.mood.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = {},
                    label = { Text("Mood: ${entry.mood}") },
                    leadingIcon = { Text("😊") }
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFE5E7EB))
            Spacer(Modifier.height(16.dp))
            Text(
                entry.content.ifBlank { "(No content)" },
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 26.sp,
                color = Color(0xFF374151)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${entry.wordCount} words",
                fontSize = 12.sp,
                color = Color(0xFF9CA3AF)
            )
            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Color(0xFFE5E7EB))
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Edit")
            }
            Button(
                onClick = onDelete,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete")
            }
        }
    }
}
