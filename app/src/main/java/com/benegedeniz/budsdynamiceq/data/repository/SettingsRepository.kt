package com.benegedeniz.budsdynamiceq.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.benegedeniz.budsdynamiceq.bluetooth.BudsModel

class SettingsRepository(context: Context) {
    companion object {
        private const val PREFS_NAME = "BudsPrefs"
        const val KEY_MAC_ADDRESS = "saved_mac_address"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSavedMacAddress(): String? {
        return prefs.getString(KEY_MAC_ADDRESS, null)
    }

    fun saveMacAddress(mac: String) {
        prefs.edit().putString(KEY_MAC_ADDRESS, mac).apply()
    }

    fun clearMacAddress() {
        prefs.edit().remove(KEY_MAC_ADDRESS).apply()
    }

    fun forgetDevice(mac: String) {
        prefs.edit()
            .remove(KEY_MAC_ADDRESS)
            .remove("detected_model_$mac")
            .remove("model_override_$mac")
            .remove("experimental_gestures_enabled_$mac")
            .remove(customEqKey(mac, 1))
            .remove(customEqKey(mac, 2))
            .remove(customEqKey(mac, 3))
            .apply()
    }

    fun getCustomEqBands(mac: String?, slotIndex: Int): List<Int> {
        val raw = prefs.getString(customEqKey(mac, slotIndex), null)
            ?: mac?.let { prefs.getString(customEqKey(null, slotIndex), null) }
        return com.benegedeniz.budsdynamiceq.data.model.CustomEqualizer.parseStored(raw)
    }

    fun saveCustomEqBands(mac: String?, bands: List<Int>, slotIndex: Int) {
        prefs.edit()
            .putString(customEqKey(mac, slotIndex), com.benegedeniz.budsdynamiceq.data.model.CustomEqualizer.serialize(bands))
            .apply()
    }

    private fun customEqKey(mac: String?, slotIndex: Int): String {
        return if (mac.isNullOrBlank()) "custom_eq_bands_${slotIndex}" else "custom_eq_bands_${slotIndex}_$mac"
    }

    fun getDetectedModel(mac: String): BudsModel {
        val saved = prefs.getString("detected_model_$mac", null)
        return try {
            if (saved != null) BudsModel.valueOf(saved) else BudsModel.UNKNOWN
        } catch (e: Exception) {
            BudsModel.UNKNOWN
        }
    }

    fun saveDetectedModel(mac: String, model: BudsModel) {
        prefs.edit().putString("detected_model_$mac", model.name).apply()
    }

    fun getModelOverride(mac: String): BudsModel? {
        val overrideStr = prefs.getString("model_override_$mac", null)
        return try {
            if (overrideStr != null) BudsModel.valueOf(overrideStr) else null
        } catch (e: Exception) {
            null
        }
    }

    fun saveModelOverride(mac: String, model: BudsModel?) {
        if (model != null) {
            prefs.edit().putString("model_override_$mac", model.name).apply()
        } else {
            prefs.edit().remove("model_override_$mac").apply()
        }
    }

    fun isExperimentalGesturesEnabled(mac: String): Boolean {
        return prefs.getBoolean("experimental_gestures_enabled_$mac", false)
    }

    fun setExperimentalGesturesEnabled(mac: String, enabled: Boolean) {
        prefs.edit().putBoolean("experimental_gestures_enabled_$mac", enabled).apply()
    }

    fun getTileBehavior(): Int {
        return prefs.getInt("qs_tile_behavior", 0) // 0: ANC_AND_TRANSPARENT, 1: ANC_AND_OFF, 2: TRANSPARENT_AND_OFF, 3: CYCLE_ALL
    }

    fun setTileBehavior(behavior: Int) {
        prefs.edit().putInt("qs_tile_behavior", behavior).apply()
    }
}
