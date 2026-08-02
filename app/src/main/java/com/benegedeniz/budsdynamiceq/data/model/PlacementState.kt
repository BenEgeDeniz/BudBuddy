package com.benegedeniz.budsdynamiceq.data.model

enum class PlacementState(val id: Int, val displayName: String) {
    DISCONNECTED(0, "Disconnected"),
    WEARING(1, "Wearing"),
    IDLE(2, "Idle"),
    CASE(3, "In Case"),
    CLOSED_CASE(4, "Closed Case"),
    UNKNOWN(-1, "Unknown");

    companion object {
        fun fromId(id: Int): PlacementState = entries.find { it.id == id } ?: UNKNOWN
    }
}
