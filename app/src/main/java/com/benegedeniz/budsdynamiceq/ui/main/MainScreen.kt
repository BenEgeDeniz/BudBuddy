package com.benegedeniz.budsdynamiceq.ui.main

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benegedeniz.budsdynamiceq.R
import com.benegedeniz.budsdynamiceq.di.ServiceLocator
import com.benegedeniz.budsdynamiceq.ui.balance.SoundBalanceTestScreen
import com.benegedeniz.budsdynamiceq.ui.buds.BudsScreen
import com.benegedeniz.budsdynamiceq.ui.fittest.FitTestScreen
import com.benegedeniz.budsdynamiceq.ui.headshake.HeadShakeScreen
import com.benegedeniz.budsdynamiceq.ui.headshake.HeadShakeViewModel
import com.benegedeniz.budsdynamiceq.ui.rules.RulesScreen
import com.benegedeniz.budsdynamiceq.ui.rules.RulesViewModel
import com.benegedeniz.budsdynamiceq.ui.buds.BudsViewModel
import com.benegedeniz.budsdynamiceq.ui.settings.AppSettingsScreen
import com.benegedeniz.budsdynamiceq.ui.wearstate.WearStateScreen
import com.benegedeniz.budsdynamiceq.ui.wearstate.WearStateViewModel
import kotlinx.coroutines.launch

// Sub-screen enum for flag-based navigation (no NavHost lifecycle transitions)
private enum class SubScreen { NONE, FIT_TEST, WEAR_STATE, SOUND_BALANCE, SETTINGS }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen() {
    val headShakeViewModel: HeadShakeViewModel = viewModel()
    val wearStateViewModel: WearStateViewModel = viewModel()
    val rulesViewModel: RulesViewModel = viewModel()
    val budsViewModel: BudsViewModel = viewModel()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = pagerState.currentPage

    var showGesturesDisabledDialog by remember { mutableStateOf(false) }
    var showNoDeviceDialog by remember { mutableStateOf(false) }

    val appContext = LocalContext.current
    val budsController = ServiceLocator.provideBudsController(appContext)
    val savedMac by budsController.savedDeviceMac.collectAsState()
    val prefs = appContext.getSharedPreferences("BudsPrefs", Context.MODE_PRIVATE)
    var experimentalGesturesEnabled by remember(savedMac) {
        mutableStateOf(prefs.getBoolean("experimental_gestures_enabled_${savedMac ?: ""}", false))
    }

    val locked = headShakeViewModel.isUiLocked.collectAsState().value
    val uiState by rulesViewModel.uiState.collectAsState()
    val effectiveModel = uiState.effectiveModel
    val isSensorDebugScreenOpen = headShakeViewModel.isSensorDebugScreenOpen

    LaunchedEffect(effectiveModel, experimentalGesturesEnabled) {
        if (effectiveModel.isExperimentalGestures && !experimentalGesturesEnabled && selectedTab == 2) {
            pagerState.animateScrollToPage(0)
        }
    }

    // Sub-screen state — no NavHost, no lifecycle transitions
    var activeSubScreen by remember { mutableStateOf(SubScreen.NONE) }
    // Tracks the last real sub-screen so exit animation has content to slide out
    var lastVisibleSubScreen by remember { mutableStateOf(SubScreen.NONE) }
    LaunchedEffect(activeSubScreen) {
        if (activeSubScreen != SubScreen.NONE) lastVisibleSubScreen = activeSubScreen
    }

    val context = LocalContext.current
    var backPressedTime by remember { mutableLongStateOf(0L) }
    
    BackHandler(enabled = activeSubScreen != SubScreen.NONE) {
        activeSubScreen = SubScreen.NONE
    }

    BackHandler(enabled = activeSubScreen == SubScreen.NONE) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < 2000) {
            (context as? android.app.Activity)?.finish()
        } else {
            backPressedTime = currentTime
            android.widget.Toast.makeText(context, context.getString(R.string.press_back_again_to_exit), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Main pager (always alive, never unmounted) ─────────────────────
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 2,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> BudsScreen(
                    viewModel = budsViewModel,
                    onFitTestClick = { activeSubScreen = SubScreen.FIT_TEST },
                    onWearStateClick = { activeSubScreen = SubScreen.WEAR_STATE },
                    onSoundBalanceTestClick = { activeSubScreen = SubScreen.SOUND_BALANCE },
                    onSettingsClick = { activeSubScreen = SubScreen.SETTINGS },
                    modifier = Modifier.fillMaxSize()
                )
                1 -> RulesScreen(viewModel = rulesViewModel, modifier = Modifier.fillMaxSize())
                2 -> HeadShakeScreen(viewModel = headShakeViewModel, modifier = Modifier.fillMaxSize())
            }
        }

        // ── Bottom bar ─────────────────────────────────────────────────────
        val showBottomBar = !isSensorDebugScreenOpen && activeSubScreen == SubScreen.NONE
        AnimatedVisibility(
            visible = showBottomBar,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            GlassyBottomNavBar(
                selectedTab = selectedTab,
                disabledTabs = if (effectiveModel.isExperimentalGestures && !experimentalGesturesEnabled) listOf(2) else emptyList(),
                onTabSelected = { targetTabIndex ->
                    if (targetTabIndex == 2 && effectiveModel.isExperimentalGestures && !experimentalGesturesEnabled) {
                        if (savedMac == null) {
                            showNoDeviceDialog = true
                        } else {
                            showGesturesDisabledDialog = true
                        }
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetTabIndex)
                        }
                    }
                },
                showFab = selectedTab == 1 || selectedTab == 2,
                fabEnabled = if (selectedTab == 2) !locked else true,
                onFabClick = {
                    if (selectedTab == 1) {
                        rulesViewModel.isEditScreenOpen = true
                    } else if (selectedTab == 2) {
                        if (locked) return@GlassyBottomNavBar
                        if (headShakeViewModel.isMovementCancellingScreenOpen) {
                            headShakeViewModel.startNewNoiseProfileSetup()
                        } else {
                            headShakeViewModel.startNewGesture()
                        }
                    }
                }
            )
        }

        // ── Sub-screen overlays (flag-based, no NavHost) ───────────────────
        AnimatedVisibility(
            visible = activeSubScreen != SubScreen.NONE,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Use lastVisibleSubScreen so content stays rendered during exit animation
                when (lastVisibleSubScreen) {
                    SubScreen.FIT_TEST -> FitTestScreen(
                        viewModel = budsViewModel,
                        onBack = { activeSubScreen = SubScreen.NONE },
                        modifier = Modifier.fillMaxSize()
                    )
                    SubScreen.WEAR_STATE -> WearStateScreen(
                        viewModel = wearStateViewModel,
                        onBack = { activeSubScreen = SubScreen.NONE },
                        modifier = Modifier.fillMaxSize()
                    )
                    SubScreen.SOUND_BALANCE -> SoundBalanceTestScreen(
                        viewModel = budsViewModel,
                        onBack = { activeSubScreen = SubScreen.NONE },
                        modifier = Modifier.fillMaxSize()
                    )
                    SubScreen.SETTINGS -> AppSettingsScreen(
                        onBack = { activeSubScreen = SubScreen.NONE },
                        modifier = Modifier.fillMaxSize()
                    )
                    SubScreen.NONE -> {}
                }
            }
        }

        // ── Dialogs ────────────────────────────────────────────────────────
        if (showGesturesDisabledDialog) {
            AlertDialog(
                onDismissRequest = { showGesturesDisabledDialog = false },
                title = { Text(stringResource(R.string.gestures_not_supported)) },
                text = { Text(stringResource(R.string.experimental_gestures_warning)) },
                confirmButton = {
                    TextButton(onClick = {
                        prefs.edit()
                            .putBoolean("experimental_gestures_enabled_${savedMac ?: ""}", true)
                            .apply()
                        experimentalGesturesEnabled = true
                        showGesturesDisabledDialog = false
                        coroutineScope.launch { pagerState.animateScrollToPage(2) }
                    }) {
                        Text(stringResource(R.string.enable))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGesturesDisabledDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showNoDeviceDialog) {
            AlertDialog(
                onDismissRequest = { showNoDeviceDialog = false },
                title = { Text(stringResource(R.string.no_device_connected)) },
                text = { Text(stringResource(R.string.connect_the_buds_first_to_access_this_fe)) },
                confirmButton = {
                    TextButton(onClick = { showNoDeviceDialog = false }) {
                        Text(stringResource(R.string.got_it))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun GlassyBottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    showFab: Boolean,
    fabEnabled: Boolean = true,
    disabledTabs: List<Int> = emptyList(),
    onFabClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val fabProgress by animateFloatAsState(
        targetValue = if (showFab) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "fabProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .height(64.dp)
    ) {
        Layout(
            content = {
                // index 0: Pill
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(
                        Triple(stringResource(R.string.tab_home), Icons.Default.Home, 0),
                        Triple(stringResource(R.string.tab_rules), Icons.Default.GraphicEq, 1),
                        Triple(stringResource(R.string.tab_gestures), Icons.Default.Sensors, 2)
                    )

                    tabs.forEach { (label, icon, index) ->
                        val isSelected = selectedTab == index
                        val isDisabled = index in disabledTabs
                        val backgroundColor = if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f) else Color.Transparent
                        val contentColor = if (isDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .width(80.dp)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onTabSelected(index)
                                }
                                .background(backgroundColor)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(22.dp),
                                tint = contentColor
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor
                            )
                        }
                    }
                }

                // index 1: FAB — always in Layout so position can be interpolated
                FloatingActionButton(
                    onClick = {
                        if (fabProgress > 0.5f && fabEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onFabClick()
                        }
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            scaleX = fabProgress
                            scaleY = fabProgress
                            alpha = if (fabEnabled) fabProgress else fabProgress * 0.5f
                            shadowElevation = 6.dp.toPx()
                            shape = CircleShape
                        },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        ) { measurables, constraints ->
            val pillPlaceable = measurables[0].measure(constraints.copy(minWidth = 0))
            val fabPlaceable = measurables[1].measure(constraints.copy(minWidth = 0))

            val screenWidth = constraints.maxWidth
            val spacingPx = 16.dp.roundToPx()

            val centeredPillX = (screenWidth - pillPlaceable.width) / 2f
            val totalGroupWidth = pillPlaceable.width + spacingPx + fabPlaceable.width
            val groupCenteredPillX = (screenWidth - totalGroupWidth) / 2f

            // Interpolate pill position smoothly using fabProgress (0=centered, 1=grouped)
            val pillX = groupCenteredPillX + (centeredPillX - groupCenteredPillX) * (1f - fabProgress)
            val fabX = pillX + pillPlaceable.width + spacingPx

            val pillY = (constraints.maxHeight - pillPlaceable.height) / 2f
            val fabY = (constraints.maxHeight - fabPlaceable.height) / 2f

            layout(constraints.maxWidth, constraints.maxHeight) {
                pillPlaceable.placeRelative(pillX.toInt(), pillY.toInt())
                fabPlaceable.placeRelative(fabX.toInt(), fabY.toInt())
            }
        }
    }
}
