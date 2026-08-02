package com.benegedeniz.budsdynamiceq.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode

@Serializable
sealed class FlowAction {
    @Serializable
    data class SystemAction(val action: GestureAction) : FlowAction()
    
    @Serializable
    data class DelayAction(val ms: Long = 100L) : FlowAction()
    
    @Serializable
    data class AppAction(val packageName: String = "", val appName: String = "Select App") : FlowAction()

    @Serializable
    data class VolumeAction(val percentage: Int = 50) : FlowAction()

    @Serializable
    data class ModifyVolumeAction(val increase: Boolean = true, val percentage: Int = 10) : FlowAction()
    @Serializable
    @SerialName("TtsAction")
    data class TtsAction(
        val text: String = "",
        val asAnnouncement: Boolean = true
    ) : FlowAction()
}
