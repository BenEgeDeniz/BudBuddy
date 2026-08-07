package com.benegedeniz.budsdynamiceq.ui.fittest

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benegedeniz.budsdynamiceq.data.model.FitTestResult
import com.benegedeniz.budsdynamiceq.data.model.PlacementState
import com.benegedeniz.budsdynamiceq.ui.components.PageHeader
import com.benegedeniz.budsdynamiceq.ui.components.bounceClick
import com.benegedeniz.budsdynamiceq.ui.headshake.Head3DCanvas
import com.benegedeniz.budsdynamiceq.ui.theme.StatusActiveGreen
import com.benegedeniz.budsdynamiceq.ui.theme.StatusErrorRed
import androidx.compose.ui.res.stringResource
import com.benegedeniz.budsdynamiceq.R
import com.benegedeniz.budsdynamiceq.ui.buds.BudsViewModel

@Composable
fun FitTestScreen(
    viewModel: BudsViewModel,
    onBack: () -> Unit, modifier: Modifier = Modifier) {
    androidx.activity.compose.BackHandler { onBack() }
    val uiState by viewModel.uiState.collectAsState()
    val placementL = uiState.placementL
    val placementR = uiState.placementR
    val fitTestResultL = uiState.fitTestResultL
    val fitTestResultR = uiState.fitTestResultR
    val haptic = LocalHapticFeedback.current

    val lInEar = placementL == PlacementState.WEARING
    val rInEar = placementR == PlacementState.WEARING
    val bothInEar = lInEar && rInEar

    // State to track if the test is currently running
    var isTesting by remember { mutableStateOf(false) }

    // When the test finishes (results arrive), stop "testing" state
    LaunchedEffect(fitTestResultL, fitTestResultR) {
        if (fitTestResultL != FitTestResult.UNKNOWN || fitTestResultR != FitTestResult.UNKNOWN) {
            isTesting = false
        }
    }
    
    // Safety check: if earbuds are taken out during testing, stop test
    LaunchedEffect(bothInEar) {
        if (!bothInEar && isTesting) {
            viewModel.stopFitTest()
            isTesting = false
        }
    }

    DisposableEffect(Unit) {
        viewModel.setFitTestScreenOpen(true)
        onDispose {
            viewModel.setFitTestScreenOpen(false)
            viewModel.stopFitTest()
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            PageHeader(
                title = stringResource(R.string.earbud_fit_test),
                isScrolled = scrollState.value > 10,
                actionIcon = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Intro Description Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.check_if_your_earbuds_provide_a_proper_s),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3D Visualizer Canvas Container Card
            val disabledColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            val leftColor = if (!lInEar) disabledColor else {
                when (fitTestResultL) {
                    FitTestResult.BAD, FitTestResult.TEST_FAILED -> StatusErrorRed
                    FitTestResult.GOOD -> StatusActiveGreen
                    else -> MaterialTheme.colorScheme.primary
                }
            }
            val rightColor = if (!rInEar) disabledColor else {
                when (fitTestResultR) {
                    FitTestResult.BAD, FitTestResult.TEST_FAILED -> StatusErrorRed
                    FitTestResult.GOOD -> StatusActiveGreen
                    else -> MaterialTheme.colorScheme.primary
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Head3DCanvas(
                        sample = null,
                        modifier = Modifier.size(150.dp),
                        leftEarbudColor = leftColor,
                        rightEarbudColor = rightColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Left & Right Earbud Status Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EarbudStatusCard(
                    title = stringResource(R.string.left_earbud),
                    inEar = lInEar,
                    fitResult = fitTestResultL,
                    isTesting = isTesting,
                    statusColor = leftColor,
                    modifier = Modifier.weight(1f)
                )

                EarbudStatusCard(
                    title = stringResource(R.string.right_earbud),
                    inEar = rInEar,
                    fitResult = fitTestResultR,
                    isTesting = isTesting,
                    statusColor = rightColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Guidance & Tip Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (!bothInEar) {
                        StatusErrorRed.copy(alpha = 0.1f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (!bothInEar) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (!bothInEar) StatusErrorRed else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (!bothInEar) {
                            stringResource(R.string.fittest_insert_both)
                        } else {
                            stringResource(R.string.fittest_try_twisting)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!bothInEar) StatusErrorRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start / Stop Test Action Button
            val buttonBgColor by animateColorAsState(
                targetValue = if (isTesting) StatusErrorRed else MaterialTheme.colorScheme.primary,
                animationSpec = tween(300),
                label = "buttonBgColor"
            )

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (isTesting) {
                        viewModel.stopFitTest()
                        isTesting = false
                    } else {
                        viewModel.startFitTest()
                        isTesting = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .bounceClick(enabled = bothInEar),
                shape = RoundedCornerShape(25.dp),
                enabled = bothInEar,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonBgColor,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.stop_test),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.start_fit_test),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun EarbudStatusCard(
    title: String,
    inEar: Boolean,
    fitResult: FitTestResult,
    isTesting: Boolean,
    statusColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Status Badge Chip
            val chipBgColor = when {
                !inEar -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                isTesting -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                fitResult == FitTestResult.GOOD -> StatusActiveGreen.copy(alpha = 0.15f)
                fitResult == FitTestResult.BAD || fitResult == FitTestResult.TEST_FAILED -> StatusErrorRed.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }

            val chipContentColor = when {
                !inEar -> MaterialTheme.colorScheme.onSurfaceVariant
                isTesting -> MaterialTheme.colorScheme.primary
                fitResult == FitTestResult.GOOD -> StatusActiveGreen
                fitResult == FitTestResult.BAD || fitResult == FitTestResult.TEST_FAILED -> StatusErrorRed
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(chipBgColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isTesting && inEar) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = chipContentColor,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    } else if (inEar && fitResult == FitTestResult.GOOD) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = chipContentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    } else if (inEar && (fitResult == FitTestResult.BAD || fitResult == FitTestResult.TEST_FAILED)) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = chipContentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = when {
                            !inEar -> stringResource(R.string.fittest_not_in_ear)
                            isTesting -> stringResource(R.string.fittest_testing)
                            else -> getResultText(fitResult)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = chipContentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun getResultText(result: FitTestResult): String {
    return when (result) {
        FitTestResult.GOOD -> stringResource(R.string.fittest_good_fit)
        FitTestResult.BAD -> stringResource(R.string.fittest_poor_fit)
        FitTestResult.TEST_FAILED -> stringResource(R.string.fittest_test_failed)
        FitTestResult.UNKNOWN -> stringResource(R.string.fittest_ready)
    }
}
