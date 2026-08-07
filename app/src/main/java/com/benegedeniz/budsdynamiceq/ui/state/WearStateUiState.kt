package com.benegedeniz.budsdynamiceq.ui.state

import com.benegedeniz.budsdynamiceq.data.model.WearStateAction

data class WearStateUiState(
    val actions: List<WearStateAction> = emptyList(),
    val isConnected: Boolean = false
)
