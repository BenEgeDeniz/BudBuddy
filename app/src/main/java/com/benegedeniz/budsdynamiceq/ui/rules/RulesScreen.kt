package com.benegedeniz.budsdynamiceq.ui.rules

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Clear
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import org.burnoutcrew.reorderable.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.app.NotificationManagerCompat
import com.benegedeniz.budsdynamiceq.data.model.EqPreset
import com.benegedeniz.budsdynamiceq.data.model.EqRule
import com.benegedeniz.budsdynamiceq.rules.RulesEngine
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.media.SongMetadata
import com.benegedeniz.budsdynamiceq.media.GenreFetchState
import com.benegedeniz.budsdynamiceq.ui.components.SearchBarInput
import com.benegedeniz.budsdynamiceq.ui.components.bounceClick
import com.benegedeniz.budsdynamiceq.service.BudsService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(viewModel: RulesViewModel, modifier: Modifier = Modifier) {
    val rules by viewModel.rules.collectAsState()
    val currentMetadata by viewModel.currentMetadata.collectAsState()
    val recentHistory by viewModel.recentHistory.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val manualPreset by viewModel.manualPreset.collectAsState()
    val manualNoiseControl by viewModel.manualNoiseControl.collectAsState()
    val lastMatchedRule by viewModel.lastMatchedRule.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var data by remember { mutableStateOf(rules) }
    LaunchedEffect(rules, searchQuery) { 
        data = if (searchQuery.isBlank()) rules 
               else rules.filter { it.keyword.contains(searchQuery, ignoreCase = true) } 
    }
    
    val listState = rememberReorderableLazyListState(
        onMove = { from, to ->
            data = data.toMutableList().apply {
                val fromIndex = indexOfFirst { it.id == from.key }
                val toIndex = indexOfFirst { it.id == to.key }
                if (fromIndex != -1 && toIndex != -1) {
                    val item = removeAt(fromIndex)
                    add(toIndex.coerceIn(0, size), item)
                }
            }
        },
        canDragOver = { draggedOver, _ -> draggedOver.key is String },
        onDragEnd = { _, _ -> viewModel.updateRulesOrder(data) }
    )

    var showInfoDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<EqRule?>(null) }

    val isScrolled by remember { derivedStateOf { listState.listState.firstVisibleItemIndex > 0 || listState.listState.firstVisibleItemScrollOffset > 20 } }

    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState.listState,
                modifier = Modifier.fillMaxSize().reorderable(listState),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 140.dp, bottom = 120.dp)
            ) {
                item {
                    // Active Rule Sub-bar
                    if (isConnected || manualPreset != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(16.dp)
                        ) {
                            val activeRuleStr = lastMatchedRule?.keyword ?: "Default"
                            val appliedEq = if (lastMatchedRule?.preset == EqPreset.DEFAULT || lastMatchedRule == null) manualPreset?.displayName ?: "None" else lastMatchedRule?.preset?.displayName ?: "None"
                            val appliedNc = if (lastMatchedRule?.noiseControl == NoiseControlMode.DEFAULT || lastMatchedRule == null) manualNoiseControl?.displayName ?: "None" else lastMatchedRule?.noiseControl?.displayName ?: "None"

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Active Rule: $activeRuleStr", 
                                        style = MaterialTheme.typography.titleMedium, 
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Equalizer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = appliedEq, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Noise Control", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = appliedNc, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
                
                item {
                    Text(
                        text = "Global Defaults",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 24.dp)
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Default Preset Dropdown
                                var expanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = { expanded = true },
                                        modifier = Modifier.fillMaxWidth().alpha(if (isConnected) 1f else 0.5f),
                                        shape = RoundedCornerShape(16.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                        enabled = isConnected
                                    ) {
                                        Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(manualPreset?.displayName ?: "Preset", maxLines = 1)
                                    }
                                    DropdownMenu(
                                        expanded = expanded, 
                                        onDismissRequest = { expanded = false },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                                    ) {
                                        EqPreset.entries.filter { it != EqPreset.DEFAULT }.forEach { preset ->
                                            DropdownMenuItem(
                                                text = { Text(preset.displayName) },
                                                onClick = {
                                                    viewModel.setManualPreset(preset)
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                
                                // Default NC Dropdown
                                var ncExpanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = { ncExpanded = true },
                                        modifier = Modifier.fillMaxWidth().alpha(if (isConnected) 1f else 0.5f),
                                        shape = RoundedCornerShape(16.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                        enabled = isConnected
                                    ) {
                                        Icon(Icons.Default.Hearing, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(manualNoiseControl?.displayName ?: "NC Mode", maxLines = 1)
                                    }
                                    DropdownMenu(
                                        expanded = ncExpanded, 
                                        onDismissRequest = { ncExpanded = false },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                                    ) {
                                        NoiseControlMode.entries.filter { it != NoiseControlMode.DEFAULT }.forEach { ncMode ->
                                            DropdownMenuItem(
                                                text = { Text(ncMode.displayName) },
                                                onClick = {
                                                    viewModel.setManualNoiseControl(ncMode)
                                                    ncExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                item {
                    SearchBarInput(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholderText = "Search rules...",
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                

                // Rules List
                if (rules.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No rules yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Tap + to create your first music rule.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                items(data, key = { it.id }) { rule ->
                    ReorderableItem(listState, key = rule.id) { isDragging ->
                        val elevation = animateDpAsState(if (isDragging) 16.dp else 0.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .animateItem()
                                .shadow(elevation.value, RoundedCornerShape(24.dp))
                        ) {
                            val isMatched = lastMatchedRule?.id == rule.id
                            RuleItem(
                                rule = rule,
                                dragModifier = if (searchQuery.isBlank()) Modifier.detectReorder(listState) else Modifier,
                                isMatched = isMatched,
                                onToggle = { enabled -> viewModel.toggleRule(rule, enabled) },
                                onEdit = { editingRule = rule },
                                onDelete = { viewModel.deleteRule(rule.id) }
                            )
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
                }
            }

            com.benegedeniz.budsdynamiceq.ui.components.PageHeader(
                title = "Music Rules",
                isScrolled = isScrolled,
                actionIcon = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About Music Rules",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    }

    if (viewModel.isEditScreenOpen || editingRule != null) {
        RuleEditScreen(
            initialRule = editingRule,
            currentMetadata = currentMetadata,
            recentHistory = recentHistory,
            onDismiss = { 
                viewModel.isEditScreenOpen = false
                editingRule = null
            },
            onSave = { keyword, preset, ncMode ->
                if (editingRule != null) {
                    viewModel.updateRule(editingRule!!.copy(keyword = keyword, preset = preset, noiseControl = ncMode))
                } else {
                    viewModel.addRule(keyword, preset, ncMode)
                }
                viewModel.isEditScreenOpen = false
                editingRule = null
            }
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text("About Music Rules", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(
                    "Music Rules automatically adjust your earbuds' EQ and Noise Control settings based on what song or media is currently playing. Create rules with keywords, and whenever that keyword matches the track title or artist, your presets are applied automatically.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
fun RuleItem(
    rule: EqRule,
    dragModifier: Modifier = Modifier,
    isMatched: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isMatched) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "bgColor"
    )
    val titleColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isMatched) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "titleColor"
    )
    val subtitleColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isMatched) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "subtitleColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = bgColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth().alpha(if (rule.enabled) 1f else 0.5f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onEdit() }
                        .padding(4.dp)
                ) {
                    Text(
                        text = "\"${rule.keyword}\"",
                        style = MaterialTheme.typography.titleLarge,
                        color = titleColor,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${rule.preset.displayName}  •  ${rule.noiseControl.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = subtitleColor
                    )
                }
                
                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onToggle(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle, 
                    contentDescription = "Drag to reorder", 
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragModifier.padding(8.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditScreen(
    initialRule: EqRule?,
    currentMetadata: com.benegedeniz.budsdynamiceq.media.SongMetadata?,
    recentHistory: List<com.benegedeniz.budsdynamiceq.media.SongMetadata>,
    onDismiss: () -> Unit,
    onSave: (String, EqPreset, NoiseControlMode) -> Unit
) {
    var keyword by remember { mutableStateOf(initialRule?.keyword ?: "") }
    var selectedPreset by remember { mutableStateOf(initialRule?.preset ?: EqPreset.DEFAULT) }
    var selectedNc by remember { mutableStateOf(initialRule?.noiseControl ?: NoiseControlMode.DEFAULT) }
    var expanded by remember { mutableStateOf(false) }
    var ncExpanded by remember { mutableStateOf(false) }

    var titleSelected by remember { mutableStateOf(false) }
    var artistSelected by remember { mutableStateOf(false) }
    var genreSelected by remember { mutableStateOf(false) }

    val isBadgeSelected = titleSelected || artistSelected || genreSelected

    val isMatch = remember(keyword, currentMetadata) {
        if (keyword.isBlank()) false
        else RulesEngine().evaluate(currentMetadata, listOf(EqRule(keyword = keyword, preset = EqPreset.DEFAULT, noiseControl = NoiseControlMode.DEFAULT, priority = 0))) != null
    }

    fun updateKeywordFromBadges() {
        val parts = mutableListOf<String>()
        if (titleSelected) currentMetadata?.title?.takeIf { it.isNotBlank() }?.let { parts.add("title:$it") }
        if (artistSelected) currentMetadata?.artist?.takeIf { it.isNotBlank() }?.let { parts.add("artist:$it") }
        if (genreSelected) currentMetadata?.genre?.takeIf { it.isNotBlank() }?.let { parts.add("genre:$it") }
        keyword = parts.joinToString(" ")
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        androidx.compose.material3.Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                androidx.compose.material3.CenterAlignedTopAppBar(
                    title = { 
                        Text(if (initialRule == null) "Add Music Rule" else "Edit Music Rule") 
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                val navBarBottom = androidx.compose.foundation.layout.WindowInsets.navigationBars
                    .asPaddingValues()
                    .calculateBottomPadding()
                val bottomPadding = if (navBarBottom > 0.dp) navBarBottom + 24.dp else 56.dp

                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 3.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = bottomPadding)
                    ) {
                        Button(
                            onClick = { onSave(keyword, selectedPreset, selectedNc) },
                            enabled = keyword.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text("Save Rule", fontSize = 16.sp)
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    // Keyword Input
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { 
                        keyword = it
                        if (it.isBlank()) {
                            titleSelected = false
                            artistSelected = false
                            genreSelected = false
                        }
                    },
                    label = { Text(if (isBadgeSelected) "Keyword (auto-generated)" else "Keyword (case-insensitive)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (keyword.isNotEmpty()) {
                            IconButton(onClick = {
                                keyword = ""
                                titleSelected = false
                                artistSelected = false
                                genreSelected = false
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )
                
                AnimatedVisibility(
                    visible = keyword.isNotBlank(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isMatch) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Matches", tint = com.benegedeniz.budsdynamiceq.ui.theme.StatusActiveGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Matches current song", color = com.benegedeniz.budsdynamiceq.ui.theme.StatusActiveGreen, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Icon(Icons.Default.Cancel, contentDescription = "No match", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Does not match current song", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                // Current Media Section
                AnimatedVisibility(
                    visible = currentMetadata != null && (!currentMetadata.title.isNullOrBlank() || !currentMetadata.artist.isNullOrBlank() || !currentMetadata.genre.isNullOrBlank() || currentMetadata.genreFetchState == GenreFetchState.LOADING),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Text(
                            text = "Current Media",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap badges to build your rule.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            currentMetadata?.title?.takeIf { it.isNotBlank() }?.let { title ->
                                FilterChip(
                                    selected = titleSelected,
                                    onClick = { 
                                        titleSelected = !titleSelected
                                        updateKeywordFromBadges()
                                    },
                                    label = { Text("Title: $title") }
                                )
                            }
                            currentMetadata?.artist?.takeIf { it.isNotBlank() }?.let { artist ->
                                FilterChip(
                                    selected = artistSelected,
                                    onClick = { 
                                        artistSelected = !artistSelected
                                        updateKeywordFromBadges()
                                    },
                                    label = { Text("Artist: $artist") }
                                )
                            }
                            when (currentMetadata?.genreFetchState) {
                                GenreFetchState.LOADING -> {
                                    FilterChip(
                                        selected = false,
                                        onClick = { },
                                        label = { Text("Fetching Genre...") },
                                        trailingIcon = { CircularProgressIndicator(modifier = Modifier.size(16.dp)) }
                                    )
                                }
                                GenreFetchState.SUCCESS, GenreFetchState.NONE, null -> {
                                    currentMetadata?.genre?.takeIf { it.isNotBlank() }?.let { genre ->
                                        FilterChip(
                                            selected = genreSelected,
                                            onClick = { 
                                                genreSelected = !genreSelected
                                                updateKeywordFromBadges()
                                            },
                                            label = { Text("Genre: $genre") }
                                        )
                                    }
                                }
                                GenreFetchState.ERROR -> {
                                    // Do not show the genre badge on error
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                var historyExpanded by remember { mutableStateOf(false) }
                if (recentHistory.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .clickable { historyExpanded = !historyExpanded }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "History", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recently Played",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (historyExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand/Collapse",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        AnimatedVisibility(
                            visible = historyExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                recentHistory.forEach { song ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                titleSelected = false
                                                artistSelected = false
                                                genreSelected = false
                                                
                                                val parts = mutableListOf<String>()
                                                if (!song.title.isNullOrBlank()) parts.add("title:${song.title}")
                                                if (!song.artist.isNullOrBlank()) parts.add("artist:${song.artist}")
                                                if (!song.genre.isNullOrBlank()) parts.add("genre:${song.genre}")
                                                
                                                keyword = parts.joinToString(" ")
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(text = song.title ?: "Unknown Title", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                            Text(text = song.artist ?: "Unknown Artist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Presets Dropdowns
                Text(
                    text = "Action",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedPreset.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Equalizer") },
                            shape = RoundedCornerShape(16.dp),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            EqPreset.entries.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.displayName) },
                                    onClick = {
                                        selectedPreset = preset
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = ncExpanded,
                        onExpandedChange = { ncExpanded = !ncExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedNc.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Noise Control") },
                            shape = RoundedCornerShape(16.dp),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ncExpanded) },
                            modifier = Modifier
                                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = ncExpanded,
                            onDismissRequest = { ncExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            NoiseControlMode.entries.forEach { ncMode ->
                                DropdownMenuItem(
                                    text = { Text(ncMode.displayName) },
                                    onClick = {
                                        selectedNc = ncMode
                                        ncExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
