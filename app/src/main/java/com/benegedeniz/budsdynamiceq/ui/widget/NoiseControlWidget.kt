package com.benegedeniz.budsdynamiceq.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.benegedeniz.budsdynamiceq.R
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.data.model.PlacementState
import com.benegedeniz.budsdynamiceq.di.ServiceLocator
import com.benegedeniz.budsdynamiceq.MainActivity
import androidx.glance.appwidget.cornerRadius

val PrimaryColor = androidx.glance.color.ColorProvider(day = Color(0xFF0381FE), night = Color(0xFF3E92FF))
val SurfaceColor = androidx.glance.color.ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1C1C1E))
val SurfaceVariantColor = androidx.glance.color.ColorProvider(day = Color(0xFFE5E5EA), night = Color(0xFF2C2C2E))
val OnSurfaceColor = androidx.glance.color.ColorProvider(day = Color(0xFF000000), night = Color(0xFFFFFFFF))
val OnSurfaceVariantColor = androidx.glance.color.ColorProvider(day = Color(0xFF3A3A3C), night = Color(0xFFEBEBF5))

class NoiseControlWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val budsController = ServiceLocator.provideBudsController(context)
        
        provideContent {
            val forceUpdate = androidx.glance.currentState(key = androidx.datastore.preferences.core.booleanPreferencesKey("force_update"))
            
            val isConnected = budsController.isConnected.value
            val placementL = budsController.placementL.value
            val placementR = budsController.placementR.value
            val oneEarbudNoiseControlEnabled = budsController.oneEarbudNoiseControlEnabled.value
            val activeNoiseControl = budsController.activeNoiseControl.value ?: NoiseControlMode.OFF
            
            val batteryL = budsController.batteryL.value
            val batteryR = budsController.batteryR.value

            WidgetContent(
                isConnected = isConnected,
                placementL = placementL,
                placementR = placementR,
                oneEarbudNoiseControlEnabled = oneEarbudNoiseControlEnabled,
                activeNoiseControl = activeNoiseControl,
                batteryL = batteryL,
                batteryR = batteryR,
                effectiveModel = budsController.effectiveModel.value
            )
        }
    }
}

@Composable
fun WidgetContent(
    isConnected: Boolean,
    placementL: PlacementState,
    placementR: PlacementState,
    oneEarbudNoiseControlEnabled: Boolean,
    activeNoiseControl: NoiseControlMode,
    batteryL: Int,
    batteryR: Int,
    effectiveModel: com.benegedeniz.budsdynamiceq.bluetooth.BudsModel
) {
    val bothInEar = isConnected && placementL == PlacementState.WEARING && placementR == PlacementState.WEARING
    val anyInEar = isConnected && (placementL == PlacementState.WEARING || placementR == PlacementState.WEARING)

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_surface_bg))
            .padding(8.dp), // Uniform padding top, bottom, left, right
        verticalAlignment = Alignment.CenterVertically
    ) {
        val subPillModifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_surface_variant_bg))
            .let {
                if (!isConnected) it.clickable(actionStartActivity<MainActivity>())
                else it
            }

        // The Massive Unified Sub-Pill
        Row(
            modifier = subPillModifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val controls = buildList {
                add(NoiseControlMode.OFF)
                if (effectiveModel.supportsTransparencyNC) add(NoiseControlMode.TRANSPARENT)
                if (effectiveModel.supportsAdaptiveNC) add(NoiseControlMode.ADAPTIVE)
                add(NoiseControlMode.NOISE_CANCELLATION)
            }

            controls.forEach { mode ->
                val isSelected = activeNoiseControl == mode
                val isModeEnabled = isConnected && anyInEar && (bothInEar || oneEarbudNoiseControlEnabled || (mode != NoiseControlMode.ADAPTIVE && mode != NoiseControlMode.NOISE_CANCELLATION && mode != NoiseControlMode.TRANSPARENT))

                val iconRes = when (mode) {
                    NoiseControlMode.NOISE_CANCELLATION -> R.drawable.ic_noise_cancellation
                    NoiseControlMode.OFF -> R.drawable.ic_close
                    NoiseControlMode.TRANSPARENT -> R.drawable.ic_transparent
                    NoiseControlMode.ADAPTIVE -> R.drawable.ic_adaptive
                    else -> R.drawable.ic_close
                }

                val iconTint = if (isSelected) SurfaceColor else OnSurfaceVariantColor

                val baseModifier = GlanceModifier.size(56.dp) 

                // Give each button a flex slot so they spread out perfectly
                Box(
                    modifier = GlanceModifier.defaultWeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isModeEnabled) {
                        val boxMod = baseModifier
                            .clickable(
                                actionRunCallback<NoiseControlActionCallback>(
                                    androidx.glance.action.actionParametersOf(
                                        NoiseControlActionCallback.NoiseControlModeKey to mode.name
                                    )
                                )
                            )
                            .background(if (isSelected) ImageProvider(R.drawable.widget_primary_bg) else ImageProvider(R.drawable.widget_transparent_bg))
                        
                        Box(
                            modifier = boxMod,
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(iconRes),
                                contentDescription = androidx.glance.LocalContext.current.getString(mode.displayNameRes),
                                colorFilter = ColorFilter.tint(iconTint),
                                modifier = GlanceModifier.size(28.dp) 
                            )
                        }
                    } else {
                        // Disabled state looks completely flat inside the pill
                        Box(
                            modifier = baseModifier.background(ImageProvider(R.drawable.widget_transparent_bg)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(iconRes),
                                contentDescription = androidx.glance.LocalContext.current.getString(mode.displayNameRes),
                                colorFilter = ColorFilter.tint(androidx.glance.color.ColorProvider(day = Color(0xFFA0A0A3), night = Color(0xFF565657))),
                                modifier = GlanceModifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        
            // Status Right Side
            if (anyInEar) {
                // Vertical Line Divider
                Box(
                    modifier = GlanceModifier
                        .width(1.dp)
                        .height(32.dp) // Explicit fixed short height
                        .background(androidx.glance.color.ColorProvider(day = Color(0x22000000), night = Color(0x33FFFFFF))) // Faint
                ) {}
                
                val topText = if (bothInEar) "L • R" else if (placementL == PlacementState.WEARING) "L" else "R"
                val bottomText = if (bothInEar) {
                    val avg = if (batteryL >= 0 && batteryR >= 0) (batteryL + batteryR) / 2 else if (batteryL >= 0) batteryL else if (batteryR >= 0) batteryR else -1
                    if (avg >= 0) "$avg%" else "--"
                } else if (placementL == PlacementState.WEARING) {
                    if (batteryL >= 0) "$batteryL%" else "--"
                } else {
                    if (batteryR >= 0) "$batteryR%" else "--"
                }
                
                Box(
                    modifier = GlanceModifier
                        .width(64.dp)
                        .fillMaxHeight() // Takes full height but content is centered
                        .clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = topText,
                            style = TextStyle(
                                color = OnSurfaceColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = bottomText,
                            style = TextStyle(
                                color = OnSurfaceColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        )
                    }
                }
            }
        }
    }
}
