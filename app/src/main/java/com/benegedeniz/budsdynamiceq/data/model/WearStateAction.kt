package com.benegedeniz.budsdynamiceq.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class WearStateAction(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val trigger: WearStateTrigger,
    val actions: List<FlowAction> = emptyList(),
    val enabled: Boolean = true
)

@Serializable
enum class WearStateTrigger(val displayName: String) {
    EARBUD_REMOVED("Earbud Removed"),
    BOTH_WEARING("Both Earbuds Worn")
}
