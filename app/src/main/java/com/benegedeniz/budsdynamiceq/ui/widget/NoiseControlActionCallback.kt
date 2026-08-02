package com.benegedeniz.budsdynamiceq.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll
import android.util.Log

class NoiseControlActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val modeStr = parameters[NoiseControlModeKey] ?: return
        val mode = try {
            NoiseControlMode.valueOf(modeStr)
        } catch (e: Exception) {
            return
        }
        
        val budsController = ServiceLocator.provideBudsController(context)
        budsController.sendNoiseControl(mode)
        
        // Update widget asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            try {
                androidx.glance.appwidget.state.updateAppWidgetState(context, glanceId) { prefs ->
                    val key = androidx.datastore.preferences.core.booleanPreferencesKey("force_update")
                    val current = prefs[key] ?: false
                    prefs[key] = !current
                }
                NoiseControlWidget().update(context, glanceId)
            } catch (e: Exception) {
                Log.e("ActionCallback", "Failed to update widget", e)
            }
        }
    }

    companion object {
        val NoiseControlModeKey = ActionParameters.Key<String>("NOISE_CONTROL_MODE")
    }
}
