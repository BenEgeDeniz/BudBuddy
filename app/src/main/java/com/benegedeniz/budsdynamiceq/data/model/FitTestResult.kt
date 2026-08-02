package com.benegedeniz.budsdynamiceq.data.model

enum class FitTestResult(val id: Int) {
    UNKNOWN(-1),
    BAD(0),
    GOOD(1),
    TEST_FAILED(2);

    companion object {
        fun fromId(id: Int): FitTestResult {
            return entries.find { it.id == id } ?: UNKNOWN
        }
    }
}
