package com.benegedeniz.budsdynamiceq.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UpdateChecker {
    sealed class UpdateStatus {
        object Idle : UpdateStatus()
        object Checking : UpdateStatus()
        data class Available(val latestVersion: String, val downloadUrl: String) : UpdateStatus()
        object UpToDate : UpdateStatus()
        data class Error(val message: String) : UpdateStatus()
    }

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.UpToDate)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private val _isUpdateAvailable = MutableStateFlow(false)
    val isUpdateAvailable: StateFlow<Boolean> = _isUpdateAvailable.asStateFlow()

    fun checkForUpdates(currentVersion: String, scope: CoroutineScope) {
        // No-op for F-Droid flavor. It's always up to date from the app's perspective.
        _updateStatus.value = UpdateStatus.UpToDate
        _isUpdateAvailable.value = false
    }
}

