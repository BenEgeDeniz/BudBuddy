package com.benegedeniz.budsdynamiceq.data.model

import kotlinx.serialization.Serializable
import java.util.UUID
import androidx.annotation.StringRes
import com.benegedeniz.budsdynamiceq.R

@Serializable
data class WearStateAction(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val trigger: WearStateTrigger,
    val actions: List<FlowAction> = emptyList(),
    val enabled: Boolean = true
)

@Serializable
enum class WearStateTrigger(@param:StringRes val displayNameRes: Int) {
    EARBUD_REMOVED(R.string.trigger_earbud_removed),
    BOTH_WEARING(R.string.trigger_both_worn)
}
