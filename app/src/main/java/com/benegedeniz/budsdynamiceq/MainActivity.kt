package com.benegedeniz.budsdynamiceq

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benegedeniz.budsdynamiceq.ui.buds.BudsScreen
import com.benegedeniz.budsdynamiceq.ui.headshake.HeadShakeScreen
import com.benegedeniz.budsdynamiceq.ui.headshake.HeadShakeViewModel
import com.benegedeniz.budsdynamiceq.ui.fittest.FitTestScreen
import com.benegedeniz.budsdynamiceq.ui.wearstate.WearStateScreen
import com.benegedeniz.budsdynamiceq.ui.wearstate.WearStateViewModel
import com.benegedeniz.budsdynamiceq.ui.rules.RulesScreen
import com.benegedeniz.budsdynamiceq.ui.rules.RulesViewModel
import com.benegedeniz.budsdynamiceq.ui.setup.SetupScreen
import com.benegedeniz.budsdynamiceq.ui.theme.BudsDynamicEQTheme
import androidx.compose.ui.res.stringResource
import com.benegedeniz.budsdynamiceq.R

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.benegedeniz.budsdynamiceq.util.LanguageUtils.setLocale(newBase))
    }

    private fun hasRequiredPermissions(): Boolean {
        val prefs = getSharedPreferences("BudsPrefs", Context.MODE_PRIVATE)
        val hasCompletedSetup = prefs.getBoolean("has_seen_app_intro", false)
        
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (hasCompletedSetup) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ANSWER_PHONE_CALLS
                )
            } else {
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.ANSWER_PHONE_CALLS
                )
            }
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ANSWER_PHONE_CALLS
            )
        }
        val systemPerms = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        val notificationPerm = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        return systemPerms && notificationPerm
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Start Foreground Service only if we have a saved device
        val prefs = getSharedPreferences("BudsPrefs", Context.MODE_PRIVATE)
        val hasSavedDevice = prefs.getString("TargetDeviceMac", null) != null
        
        if (hasSavedDevice && hasRequiredPermissions()) {
            val serviceIntent = Intent(this, com.benegedeniz.budsdynamiceq.service.BudsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        setContent {
            BudsDynamicEQTheme {
                var permissionsGranted by remember { mutableStateOf(hasRequiredPermissions()) }
                var hasSeenAppIntro by remember { 
                    mutableStateOf(getSharedPreferences("BudsPrefs", Context.MODE_PRIVATE).getBoolean("has_seen_app_intro", false)) 
                }
                
                if (permissionsGranted) {
                    if (!hasSeenAppIntro) {
                        com.benegedeniz.budsdynamiceq.ui.setup.AppIntroScreen(
                            onIntroFinished = {
                                getSharedPreferences("BudsPrefs", Context.MODE_PRIVATE).edit().putBoolean("has_seen_app_intro", true).apply()
                                hasSeenAppIntro = true
                            }
                        )
                    } else {
                        var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
                        var showFitTest by remember { mutableStateOf(false) }
                        var showWearState by remember { mutableStateOf(false) }
                        var showSoundBalanceTest by remember { mutableStateOf(false) }
                        var showAppSettings by remember { mutableStateOf(false) }
                        var showGesturesDisabledDialog by remember { mutableStateOf(false) }
                        var showNoDeviceDialog by remember { mutableStateOf(false) }
                        
                        val appCoroutineScope = rememberCoroutineScope()
                        val appContext = androidx.compose.ui.platform.LocalContext.current
                        val budsController = com.benegedeniz.budsdynamiceq.di.ServiceLocator.provideBudsController(appContext)
                        val savedMac by budsController.savedDeviceMac.collectAsState()
                        val prefs = appContext.getSharedPreferences("BudsPrefs", android.content.Context.MODE_PRIVATE)
                        var experimentalGesturesEnabled by remember(savedMac) { 
                            mutableStateOf(prefs.getBoolean("experimental_gestures_enabled_${savedMac ?: ""}", false)) 
                        }
                        LaunchedEffect(Unit) {
                            val versionName = try {
                                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "N/A"
                            } catch (e: Exception) {
                                "N/A"
                            }
                            com.benegedeniz.budsdynamiceq.util.UpdateChecker.checkForUpdates(versionName, appCoroutineScope)
                        }
                        
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            val headShakeViewModel: HeadShakeViewModel = viewModel()
                            val wearStateViewModel: WearStateViewModel = viewModel()
                            val rulesViewModel: RulesViewModel = viewModel()
                            Box(modifier = Modifier.fillMaxSize()) {
                            androidx.compose.animation.AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                if (targetState > initialState) {
                                    (androidx.compose.animation.slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(300)) { width -> width } + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300))).togetherWith(
                                        androidx.compose.animation.slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(300)) { width -> -width } + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                                    )
                                } else {
                                    (androidx.compose.animation.slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(300)) { width -> -width } + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300))).togetherWith(
                                        androidx.compose.animation.slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(300)) { width -> width } + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                                    )
                                }
                            },
                            label = "Tab Transition",
                            modifier = Modifier.fillMaxSize()
                        ) { targetTab ->
                            when (targetTab) {
                                0 -> {
                                    androidx.compose.animation.AnimatedContent(
                                        targetState = listOf(showFitTest, showWearState, showSoundBalanceTest, showAppSettings),
                                        transitionSpec = {
                                            if (targetState.any { it }) {
                                                (androidx.compose.animation.slideInVertically(animationSpec = androidx.compose.animation.core.tween(300)) { height -> height } + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300))).togetherWith(
                                                    androidx.compose.animation.slideOutVertically(animationSpec = androidx.compose.animation.core.tween(300)) { height -> -height / 3 } + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                                                )
                                            } else {
                                                (androidx.compose.animation.slideInVertically(animationSpec = androidx.compose.animation.core.tween(300)) { height -> -height / 3 } + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300))).togetherWith(
                                                    androidx.compose.animation.slideOutVertically(animationSpec = androidx.compose.animation.core.tween(300)) { height -> height } + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                                                )
                                            }
                                        },
                                        label = "HomeTransition"
                                    ) { (isFitTestOpen, isWearStateOpen, isSoundBalanceTestOpen, isAppSettingsOpen) ->
                                        if (isFitTestOpen) {
                                            FitTestScreen(
                                                viewModel = rulesViewModel,
                                                onBack = { showFitTest = false },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else if (isWearStateOpen) {
                                            WearStateScreen(
                                                viewModel = wearStateViewModel,
                                                onBack = { showWearState = false },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else if (isSoundBalanceTestOpen) {
                                            com.benegedeniz.budsdynamiceq.ui.balance.SoundBalanceTestScreen(
                                                viewModel = rulesViewModel,
                                                onBack = { showSoundBalanceTest = false },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else if (isAppSettingsOpen) {
                                            com.benegedeniz.budsdynamiceq.ui.settings.AppSettingsScreen(
                                                onBack = { showAppSettings = false },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            BudsScreen(
                                                viewModel = rulesViewModel,
                                                onFitTestClick = { showFitTest = true },
                                                onWearStateClick = { showWearState = true },
                                                onSoundBalanceTestClick = { showSoundBalanceTest = true },
                                                onSettingsClick = { showAppSettings = true },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                                1 -> {
                                    RulesScreen(viewModel = rulesViewModel, modifier = Modifier.fillMaxSize())
                                }
                                2 -> {
                                    HeadShakeScreen(viewModel = headShakeViewModel, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                        
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !showFitTest && !showWearState && !showSoundBalanceTest && !showAppSettings,
                            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth(),
                            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
                            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
                        ) {
                            val locked = headShakeViewModel.isUiLocked.collectAsState().value
                            val effectiveModel = rulesViewModel.effectiveModel.collectAsState().value
                            val savedDeviceMac = rulesViewModel.savedDeviceMac.collectAsState().value
                            val context = androidx.compose.ui.platform.LocalContext.current
                            
                            androidx.compose.runtime.LaunchedEffect(effectiveModel, experimentalGesturesEnabled) {
                                if (effectiveModel != com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.BUDS_4_PRO && !experimentalGesturesEnabled && selectedTab == 2) {
                                    selectedTab = 0
                                }
                            }
                            
                            GlassyBottomNavBar(
                                selectedTab = selectedTab,
                                disabledTabs = if (effectiveModel != com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.BUDS_4_PRO && !experimentalGesturesEnabled) listOf(2) else emptyList(),
                                onTabSelected = { 
                                    if (it == 2 && effectiveModel != com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.BUDS_4_PRO && !experimentalGesturesEnabled) {
                                        if (savedDeviceMac == null) {
                                            showNoDeviceDialog = true
                                        } else {
                                            showGesturesDisabledDialog = true
                                        }
                                    } else {
                                        selectedTab = it 
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
                                        appContext.getSharedPreferences("BudsPrefs", android.content.Context.MODE_PRIVATE)
                                            .edit()
                                            .putBoolean("experimental_gestures_enabled_${savedMac ?: ""}", true)
                                            .apply()
                                        experimentalGesturesEnabled = true
                                        showGesturesDisabledDialog = false 
                                        selectedTab = 2
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
                    }
                } else {
                    SetupScreen(
                        onPermissionsGranted = {
                            permissionsGranted = true
                        }
                    )
                }
            }
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
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val fabProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showFab) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "fabProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .height(64.dp)
    ) {
        androidx.compose.ui.layout.Layout(
            content = {
                // index 0: Pill
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .shadow(6.dp, androidx.compose.foundation.shape.CircleShape)
                        .clip(androidx.compose.foundation.shape.CircleShape)
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
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
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
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
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
                                shape = androidx.compose.foundation.shape.CircleShape
                            },
                        shape = androidx.compose.foundation.shape.CircleShape,
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