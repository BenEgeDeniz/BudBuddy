package com.benegedeniz.budsdynamiceq.ui.main

import android.content.Context
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.benegedeniz.budsdynamiceq.R
import com.benegedeniz.budsdynamiceq.di.ServiceLocator
import com.benegedeniz.budsdynamiceq.ui.buds.BudsScreen
import com.benegedeniz.budsdynamiceq.ui.fittest.FitTestScreen
import com.benegedeniz.budsdynamiceq.ui.headshake.HeadShakeScreen
import com.benegedeniz.budsdynamiceq.ui.headshake.HeadShakeViewModel
import com.benegedeniz.budsdynamiceq.ui.rules.RulesScreen
import com.benegedeniz.budsdynamiceq.ui.rules.RulesViewModel
import com.benegedeniz.budsdynamiceq.ui.wearstate.WearStateScreen
import com.benegedeniz.budsdynamiceq.ui.wearstate.WearStateViewModel
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val tabIndex: Int = -1) {
    object Home : Screen("home", 0)
    object Rules : Screen("rules", 1)
    object Gestures : Screen("gestures", 2)
    object FitTest : Screen("fit_test")
    object WearState : Screen("wear_state")
    object SoundBalance : Screen("sound_balance")
    object Settings : Screen("settings")
}

@Composable
fun MainScreen() {
    val headShakeViewModel: HeadShakeViewModel = viewModel()
    val wearStateViewModel: WearStateViewModel = viewModel()
    val rulesViewModel: RulesViewModel = viewModel()
    
    val navController = rememberNavController()
    
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "main_tabs",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("main_tabs") {
                MainTabsScreen(
                    navController = navController,
                    headShakeViewModel = headShakeViewModel,
                    rulesViewModel = rulesViewModel
                )
            }
            
            // Sub-screens of Home
            composable(Screen.FitTest.route) {
                FitTestScreen(
                    viewModel = rulesViewModel,
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            composable(Screen.WearState.route) {
                WearStateScreen(
                    viewModel = wearStateViewModel,
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            composable(Screen.SoundBalance.route) {
                com.benegedeniz.budsdynamiceq.ui.balance.SoundBalanceTestScreen(
                    viewModel = rulesViewModel,
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            composable(Screen.Settings.route) {
                com.benegedeniz.budsdynamiceq.ui.settings.AppSettingsScreen(
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainTabsScreen(
    navController: NavHostController,
    headShakeViewModel: HeadShakeViewModel,
    rulesViewModel: RulesViewModel
) {
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 3 }
    )
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
    val effectiveModel = rulesViewModel.effectiveModel.collectAsState().value
    val isSensorDebugScreenOpen = headShakeViewModel.isSensorDebugScreenOpen
    
    LaunchedEffect(effectiveModel, experimentalGesturesEnabled) {
        if (effectiveModel.isExperimentalGestures && !experimentalGesturesEnabled && selectedTab == 2) {
            pagerState.scrollToPage(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 2,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> BudsScreen(
                    viewModel = rulesViewModel,
                    onFitTestClick = { navController.navigate(Screen.FitTest.route) },
                    onWearStateClick = { navController.navigate(Screen.WearState.route) },
                    onSoundBalanceTestClick = { navController.navigate(Screen.SoundBalance.route) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    modifier = Modifier.fillMaxSize()
                )
                1 -> RulesScreen(viewModel = rulesViewModel, modifier = Modifier.fillMaxSize())
                2 -> HeadShakeScreen(viewModel = headShakeViewModel, modifier = Modifier.fillMaxSize())
            }
        }
        
        val showBottomBar = !isSensorDebugScreenOpen
        
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
                            // Instant scroll avoids visual glitches on heavy screens
                            pagerState.scrollToPage(targetTabIndex)
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
                        coroutineScope.launch { pagerState.scrollToPage(2) }
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

                // index 1: FAB
                if (fabProgress > 0f) {
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
            }
        ) { measurables, constraints ->
            val pillPlaceable = measurables[0].measure(constraints.copy(minWidth = 0))
            val fabPlaceable = if (measurables.size > 1) measurables[1].measure(constraints.copy(minWidth = 0)) else null
            
            val screenWidth = constraints.maxWidth
            val fabWidth = fabPlaceable?.width ?: 0
            val spacingPx = 16.dp.roundToPx()
            
            // Fixed centered position for the pill when alone
            val centeredPillX = (screenWidth - pillPlaceable.width) / 2f
            
            // Shifted position when FAB is present so the entire group remains centered
            val totalGroupWidth = pillPlaceable.width + spacingPx + fabWidth
            val groupCenteredPillX = (screenWidth - totalGroupWidth) / 2f
            
            val currentPillX = centeredPillX + (groupCenteredPillX - centeredPillX) * fabProgress
            val fabX = currentPillX + pillPlaceable.width + (spacingPx * fabProgress)
            
            layout(screenWidth, pillPlaceable.height) {
                pillPlaceable.placeRelative(currentPillX.toInt(), 0)
                if (fabPlaceable != null && fabWidth > 0) {
                    fabPlaceable.placeRelative(fabX.toInt(), (pillPlaceable.height - fabPlaceable.height) / 2)
                }
            }
        }
    }
}
