package com.benegedeniz.budsdynamiceq.ui.wearstate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benegedeniz.budsdynamiceq.bluetooth.BudsController
import com.benegedeniz.budsdynamiceq.data.WearStateRepository
import com.benegedeniz.budsdynamiceq.data.model.FlowAction
import com.benegedeniz.budsdynamiceq.data.model.WearStateAction
import com.benegedeniz.budsdynamiceq.data.model.WearStateTrigger
import com.benegedeniz.budsdynamiceq.di.ServiceLocator
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WearStateViewModel(application: Application) : AndroidViewModel(application) {

    private val wearStateRepo = ServiceLocator.provideWearStateRepository(application)
    private val budsController = ServiceLocator.provideBudsController(application)

    val wearStateActions: StateFlow<List<WearStateAction>> = wearStateRepo.actions
    val isConnected: StateFlow<Boolean> = budsController.isConnected

    init {
        viewModelScope.launch {
            wearStateRepo.loadActions()
        }
    }

    fun deleteAction(id: String) {
        viewModelScope.launch {
            wearStateRepo.deleteAction(id)
        }
    }

    fun toggleAction(action: WearStateAction, enabled: Boolean) {
        viewModelScope.launch {
            wearStateRepo.updateAction(action.copy(enabled = enabled))
        }
    }

    fun saveAction(action: WearStateAction) {
        viewModelScope.launch {
            val existing = wearStateActions.value.find { it.id == action.id }
            if (existing != null) {
                wearStateRepo.updateAction(action)
            } else {
                wearStateRepo.addAction(action)
            }
        }
    }
}
