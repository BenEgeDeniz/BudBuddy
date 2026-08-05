package com.benegedeniz.budsdynamiceq.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val UPDATE_URL = "https://gist.githubusercontent.com/BenEgeDeniz/725cc49726f3ebd3e24b964f5803c8c0/raw/gistfile1.txt"
    const val RELEASES_URL = "https://github.com/BenEgeDeniz/BudBuddy/releases"

    sealed class UpdateStatus {
        object Idle : UpdateStatus()
        object Checking : UpdateStatus()
        data class Available(val latestVersion: String, val downloadUrl: String) : UpdateStatus()
        object UpToDate : UpdateStatus()
        data class Error(val message: String) : UpdateStatus()
    }

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private val _isUpdateAvailable = MutableStateFlow(false)
    val isUpdateAvailable: StateFlow<Boolean> = _isUpdateAvailable.asStateFlow()

    fun checkForUpdates(currentVersion: String, scope: CoroutineScope) {
        if (currentVersion.isEmpty() || currentVersion == "N/A") return
        
        scope.launch(Dispatchers.IO) {
            _updateStatus.value = UpdateStatus.Checking
            try {
                val url = URL(UPDATE_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"
                connection.useCaches = false
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val rawText = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                    if (rawText.isNotEmpty() && isVersionLower(currentVersion, rawText)) {
                        _isUpdateAvailable.value = true
                        _updateStatus.value = UpdateStatus.Available(rawText, RELEASES_URL)
                    } else {
                        _isUpdateAvailable.value = false
                        _updateStatus.value = UpdateStatus.UpToDate
                    }
                } else {
                    _updateStatus.value = UpdateStatus.Error("HTTP $responseCode")
                }
            } catch (e: Exception) {
                _updateStatus.value = UpdateStatus.Error(e.localizedMessage ?: "Network error")
            }
        }
    }

    fun isVersionLower(current: String, latest: String): Boolean {
        fun parse(v: String): List<Int> {
            return v.trim().trimStart('v', 'V')
                .split(".")
                .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        }
        val cParts = parse(current)
        val lParts = parse(latest)
        val maxLen = maxOf(cParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val c = cParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (c < l) return true
            if (c > l) return false
        }
        return false
    }
}
