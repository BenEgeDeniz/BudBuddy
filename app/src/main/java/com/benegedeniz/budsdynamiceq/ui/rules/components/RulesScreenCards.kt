package com.benegedeniz.budsdynamiceq.ui.rules.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.benegedeniz.budsdynamiceq.R
import com.benegedeniz.budsdynamiceq.bluetooth.BudsModel
import com.benegedeniz.budsdynamiceq.data.model.EqPreset
import com.benegedeniz.budsdynamiceq.data.model.EqRule
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode

@Composable
fun ActiveRuleCard(
    isConnected: Boolean,
    lastMatchedRule: EqRule?,
    manualPreset: EqPreset?,
    manualNoiseControl: NoiseControlMode?,
    modifier: Modifier = Modifier
) {
    if (isConnected || manualPreset != null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(16.dp)
        ) {
            val activeRuleStr = lastMatchedRule?.keyword ?: stringResource(R.string.eq_default)
            val appliedEq = if (lastMatchedRule?.preset == EqPreset.DEFAULT || lastMatchedRule == null) manualPreset?.let { stringResource(it.displayNameRes) } ?: stringResource(R.string.none) else lastMatchedRule?.preset?.let { stringResource(it.displayNameRes) } ?: stringResource(R.string.none)
            val appliedNc = if (lastMatchedRule?.noiseControl == NoiseControlMode.DEFAULT || lastMatchedRule == null) manualNoiseControl?.let { stringResource(it.displayNameRes) } ?: stringResource(R.string.none) else lastMatchedRule?.noiseControl?.let { stringResource(it.displayNameRes) } ?: stringResource(R.string.none)

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.rules_active_rule, activeRuleStr), 
                        style = MaterialTheme.typography.titleMedium, 
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.equalizer_text), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = appliedEq, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.noise_control_text), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = appliedNc, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalDefaultsCard(
    isConnected: Boolean,
    effectiveModel: BudsModel,
    manualPreset: EqPreset?,
    manualNoiseControl: NoiseControlMode?,
    onSetManualPreset: (EqPreset) -> Unit,
    onSetManualNoiseControl: (NoiseControlMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.global_defaults),
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
                            Text(manualPreset?.let { stringResource(it.displayNameRes) } ?: stringResource(R.string.preset), maxLines = 1)
                        }
                        DropdownMenu(
                            expanded = expanded, 
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        ) {
                            EqPreset.entries.filter { it != EqPreset.DEFAULT }.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(preset.displayNameRes)) },
                                    onClick = {
                                        onSetManualPreset(preset)
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
                            Text(manualNoiseControl?.let { stringResource(it.displayNameRes) } ?: stringResource(R.string.nc_mode), maxLines = 1)
                        }
                        DropdownMenu(
                            expanded = ncExpanded, 
                            onDismissRequest = { ncExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        ) {
                            NoiseControlMode.entries.filter { it != NoiseControlMode.DEFAULT && (it != NoiseControlMode.ADAPTIVE || effectiveModel.supportsAdaptiveNC) && (it != NoiseControlMode.TRANSPARENT || effectiveModel.supportsTransparencyNC) }.forEach { ncMode ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(ncMode.displayNameRes)) },
                                    onClick = {
                                        onSetManualNoiseControl(ncMode)
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
}
