package com.benegedeniz.budsdynamiceq.ui.headshake

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.benegedeniz.budsdynamiceq.R
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.benegedeniz.budsdynamiceq.bluetooth.BudsController
import com.benegedeniz.budsdynamiceq.bluetooth.BudsModel
import com.benegedeniz.budsdynamiceq.gesture.QuaternionSample
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorDebugScreen(
    budsController: BudsController,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val effectiveModel by budsController.effectiveModel.collectAsState()
    
    val isConnected by budsController.isConnected.collectAsState(false)
    val placementL by budsController.placementL.collectAsState(com.benegedeniz.budsdynamiceq.data.model.PlacementState.UNKNOWN)
    val placementR by budsController.placementR.collectAsState(com.benegedeniz.budsdynamiceq.data.model.PlacementState.UNKNOWN)
    
    val phases = listOf(
        "LEFT_CENTER" to Pair(R.string.debug_left_center_name, R.string.debug_left_center_desc),
        "LEFT_RIGHT" to Pair(R.string.debug_left_right_name, R.string.debug_left_right_desc),
        "LEFT_LEFT" to Pair(R.string.debug_left_left_name, R.string.debug_left_left_desc),
        "LEFT_UP" to Pair(R.string.debug_left_up_name, R.string.debug_left_up_desc),
        "LEFT_DOWN" to Pair(R.string.debug_left_down_name, R.string.debug_left_down_desc),
        "LEFT_CLOCKWISE" to Pair(R.string.debug_left_cw_name, R.string.debug_left_cw_desc),
        "RIGHT_CENTER" to Pair(R.string.debug_right_center_name, R.string.debug_right_center_desc),
        "RIGHT_RIGHT" to Pair(R.string.debug_right_right_name, R.string.debug_right_right_desc),
        "RIGHT_LEFT" to Pair(R.string.debug_right_left_name, R.string.debug_right_left_desc),
        "RIGHT_UP" to Pair(R.string.debug_right_up_name, R.string.debug_right_up_desc),
        "RIGHT_DOWN" to Pair(R.string.debug_right_down_name, R.string.debug_right_down_desc),
        "RIGHT_CLOCKWISE" to Pair(R.string.debug_right_cw_name, R.string.debug_right_cw_desc)
    )
    
    var currentPhaseIndex by remember { mutableIntStateOf(0) }
    var isRecording by remember { mutableStateOf(false) }
    
    // Phase Name -> List of Samples
    val recordedData = remember { mutableStateMapOf<String, List<QuaternionSample>>() }
    var currentRecording = remember { mutableStateListOf<QuaternionSample>() }

    // Listen to raw flow when recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            budsController.rawSpatialDataFlow.onEach { sample ->
                currentRecording.add(sample)
            }.launchIn(this)
        }
    }

    DisposableEffect(Unit) {
        budsController.startSpatialSensor("sensor_debug")
        onDispose {
            budsController.stopSpatialSensor("sensor_debug")
        }
    }
    
    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentPhaseIndex < phases.size) {
                val phase = phases[currentPhaseIndex]
                val phaseName = phase.first
                val phaseInstructionResId = phase.second.second
                val phaseNameResId = phase.second.first
                
                Text(
                    text = stringResource(R.string.debug_phase_x_of_n, currentPhaseIndex + 1, phases.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(phaseNameResId),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        text = stringResource(phaseInstructionResId),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (!isRecording) {
                    val hasData = recordedData.containsKey(phaseName)
                    
                    if (hasData) {
                        Text(stringResource(R.string.debug_recorded_samples, recordedData[phaseName]?.size ?: 0))
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { currentPhaseIndex++ },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.debug_next_phase))
                            }
                            OutlinedButton(
                                onClick = {
                                    currentRecording.clear()
                                    isRecording = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.debug_rerecord))
                            }
                            if (currentPhaseIndex > 0) {
                                TextButton(
                                    onClick = { currentPhaseIndex-- },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.debug_prev_phase))
                                }
                            }
                        }
                    } else {
                        val isLeftPhase = phaseName.startsWith("LEFT_")
                        
                        val lWearing = placementL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                        val rWearing = placementR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                        
                        val lDisconnected = placementL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.DISCONNECTED || placementL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.CASE || placementL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.CLOSED_CASE
                        val rDisconnected = placementR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.DISCONNECTED || placementR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.CASE || placementR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.CLOSED_CASE
                        
                        val canRecord = isConnected && if (isLeftPhase) {
                            lWearing && rDisconnected
                        } else {
                            rWearing && lDisconnected
                        }
                        
                        if (!canRecord) {
                            val msgRes = if (!isConnected) R.string.debug_record_prerequisite_disconnected
                                         else if (isLeftPhase) R.string.debug_record_prerequisite_left
                                         else R.string.debug_record_prerequisite_right
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Text(
                                    text = stringResource(msgRes),
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        Button(
                            onClick = {
                                currentRecording.clear()
                                isRecording = true
                            },
                            modifier = Modifier.size(120.dp),
                            enabled = canRecord,
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(48.dp))
                                Text(stringResource(R.string.debug_start))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(stringResource(R.string.debug_how_to_record), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(stringResource(R.string.debug_step1), color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text(stringResource(R.string.debug_step2), color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text(stringResource(R.string.debug_step3), color = MaterialTheme.colorScheme.onTertiaryContainer)
                                
                                val isMotionPhase = phaseName.contains("CLOCKWISE")
                                val step4ResId = if (isMotionPhase) R.string.debug_step4_motion else R.string.debug_step4_static
                                Text(stringResource(step4ResId), color = MaterialTheme.colorScheme.onTertiaryContainer)
                                
                                Text(stringResource(R.string.debug_step5), color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        if (currentPhaseIndex > 0) {
                            OutlinedButton(onClick = { currentPhaseIndex-- }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.debug_prev_phase))
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.debug_recording_samples, currentRecording.size),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            isRecording = false
                            recordedData[phaseName] = currentRecording.toList()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.size(120.dp),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(36.dp))
                            Text(stringResource(R.string.debug_done))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            } else {
                // All phases complete
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.debug_collection_complete),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.debug_export_msg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = { exportData(context, effectiveModel, recordedData) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.debug_export_button))
                }
            }
        }
    }
}

private fun exportData(
    context: Context,
    model: BudsModel,
    data: Map<String, List<QuaternionSample>>
) {
    val rootObj = JSONObject()
    rootObj.put("model", model.name)
    
    val phasesObj = JSONObject()
    data.forEach { (phase, samples) ->
        val samplesArray = JSONArray()
        samples.forEach { s ->
            val sampleArr = JSONArray()
            sampleArr.put(s.timestampMs)
            sampleArr.put(s.w.toDouble())
            sampleArr.put(s.x.toDouble())
            sampleArr.put(s.y.toDouble())
            sampleArr.put(s.z.toDouble())
            samplesArray.put(sampleArr)
        }
        phasesObj.put(phase, samplesArray)
    }
    rootObj.put("phases", phasesObj)
    
    val jsonString = rootObj.toString(2)
    
    val file = File(context.cacheDir, "sensor_debug_${model.name}.json")
    file.writeText(jsonString)
    
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    
    context.startActivity(Intent.createChooser(intent, "Share Sensor Debug Data"))
}
