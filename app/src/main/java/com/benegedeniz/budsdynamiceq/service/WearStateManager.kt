package com.benegedeniz.budsdynamiceq.service

import com.benegedeniz.budsdynamiceq.bluetooth.BudsController
import com.benegedeniz.budsdynamiceq.data.WearStateRepository
import com.benegedeniz.budsdynamiceq.gesture.GestureActionExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class WearStateManager(
    private val scope: CoroutineScope,
    private val budsController: BudsController,
    private val wearStateRepo: WearStateRepository,
    private val actionExecutor: GestureActionExecutor
) {
    private var prevL = com.benegedeniz.budsdynamiceq.data.model.PlacementState.UNKNOWN
    private var prevR = com.benegedeniz.budsdynamiceq.data.model.PlacementState.UNKNOWN
    private var initialized = false

    fun start() {
        scope.launch {
            combine(
                budsController.placementL,
                budsController.placementR,
                budsController.isConnected,
                wearStateRepo.actions
            ) { pL, pR, connected, actions ->
                if (!connected) {
                    initialized = false
                    prevL = com.benegedeniz.budsdynamiceq.data.model.PlacementState.UNKNOWN
                    prevR = com.benegedeniz.budsdynamiceq.data.model.PlacementState.UNKNOWN
                    return@combine
                }
                
                if (pL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.UNKNOWN && pR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.UNKNOWN) {
                    return@combine
                }

                if (!initialized) {
                    initialized = true
                    prevL = pL
                    prevR = pR
                    return@combine
                }

                val lWearing = pL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                val rWearing = pR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                val prevLWearing = prevL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                val prevRWearing = prevR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING

                val earbudRemoved = (prevLWearing && !lWearing) || (prevRWearing && !rWearing)
                val bothWearing = lWearing && rWearing && (!prevLWearing || !prevRWearing)

                val activeActions = actions.filter { it.enabled }
                
                if (earbudRemoved) {
                    activeActions.filter { it.trigger == com.benegedeniz.budsdynamiceq.data.model.WearStateTrigger.EARBUD_REMOVED }.forEach { action ->
                        scope.launch {
                            delay(300) // Small debounce for hardware settle
                            actionExecutor.execute(action.actions, false)
                        }
                    }
                }
                
                if (bothWearing) {
                    activeActions.filter { it.trigger == com.benegedeniz.budsdynamiceq.data.model.WearStateTrigger.BOTH_WEARING }.forEach { action ->
                        scope.launch {
                            delay(300) // Small debounce for hardware settle
                            actionExecutor.execute(action.actions, false)
                        }
                    }
                }

                prevL = pL
                prevR = pR
            }.collect { }
        }
    }
}
