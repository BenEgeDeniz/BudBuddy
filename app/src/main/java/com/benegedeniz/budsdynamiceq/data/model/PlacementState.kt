package com.benegedeniz.budsdynamiceq.data.model

import androidx.annotation.StringRes
import com.benegedeniz.budsdynamiceq.R

enum class PlacementState(val id: Int, @StringRes val displayNameRes: Int) {
    DISCONNECTED(0, R.string.place_disconnected),
    WEARING(1, R.string.place_wearing),
    IDLE(2, R.string.place_idle),
    CASE(3, R.string.place_case),
    CLOSED_CASE(4, R.string.place_closed_case),
    UNKNOWN(-1, R.string.place_unknown);

    companion object {
        fun fromId(id: Int): PlacementState = entries.find { it.id == id } ?: UNKNOWN
    }
}
