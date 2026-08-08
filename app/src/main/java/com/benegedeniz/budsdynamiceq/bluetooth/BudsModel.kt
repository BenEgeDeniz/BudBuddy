package com.benegedeniz.budsdynamiceq.bluetooth

enum class BudsModel(
    @param:androidx.annotation.StringRes val displayNameRes: Int,
    val supportsAdaptiveNC: Boolean,
    val supportsTransparencyNC: Boolean,
    val supportsConversationDetection: Boolean,
    val supportsFitTest: Boolean,
    val isExperimentalGestures: Boolean,
    val supportsFmgRingWhileWearing: Boolean,
    val supportsDoubleTapEdge: Boolean
) {
    BUDS_2(
        displayNameRes = com.benegedeniz.budsdynamiceq.R.string.model_buds_2,
        supportsAdaptiveNC = false,
        supportsTransparencyNC = true,
        supportsConversationDetection = false,
        supportsFitTest = true,
        isExperimentalGestures = true,
        supportsFmgRingWhileWearing = true,
        supportsDoubleTapEdge = true
    ),
    BUDS_2_PRO(
        displayNameRes = com.benegedeniz.budsdynamiceq.R.string.model_buds_2_pro,
        supportsAdaptiveNC = false,
        supportsTransparencyNC = true,
        supportsConversationDetection = true,
        supportsFitTest = true,
        isExperimentalGestures = true,
        supportsFmgRingWhileWearing = true,
        supportsDoubleTapEdge = true
    ),
    BUDS_3(
        displayNameRes = com.benegedeniz.budsdynamiceq.R.string.model_buds_3,
        supportsAdaptiveNC = false,
        supportsTransparencyNC = false,
        supportsConversationDetection = false,
        supportsFitTest = false,
        isExperimentalGestures = true,
        supportsFmgRingWhileWearing = false,
        supportsDoubleTapEdge = false
    ),
    BUDS_3_PRO(
        displayNameRes = com.benegedeniz.budsdynamiceq.R.string.model_buds_3_pro,
        supportsAdaptiveNC = true,
        supportsTransparencyNC = true,
        supportsConversationDetection = true,
        supportsFitTest = true,
        isExperimentalGestures = true,
        supportsFmgRingWhileWearing = false,
        supportsDoubleTapEdge = false
    ),
    BUDS_4_PRO(
        displayNameRes = com.benegedeniz.budsdynamiceq.R.string.model_buds_4_pro,
        supportsAdaptiveNC = true,
        supportsTransparencyNC = true,
        supportsConversationDetection = true,
        supportsFitTest = true,
        isExperimentalGestures = false,
        supportsFmgRingWhileWearing = false,
        supportsDoubleTapEdge = false
    ),
    BUDS_4(
        displayNameRes = com.benegedeniz.budsdynamiceq.R.string.model_buds_4,
        supportsAdaptiveNC = false,
        supportsTransparencyNC = false,
        supportsConversationDetection = false,
        supportsFitTest = false,
        isExperimentalGestures = false,
        supportsFmgRingWhileWearing = false,
        supportsDoubleTapEdge = false
    ),
    UNKNOWN(
        displayNameRes = com.benegedeniz.budsdynamiceq.R.string.model_buds_unknown,
        supportsAdaptiveNC = true,
        supportsTransparencyNC = true,
        supportsConversationDetection = true,
        supportsFitTest = true,
        isExperimentalGestures = true,
        supportsFmgRingWhileWearing = false,
        supportsDoubleTapEdge = false
    )
}
