package com.benegedeniz.budsdynamiceq.data.model

enum class GestureAction(val displayName: String, val group: String) {
    PLAY_PAUSE("Toggle Play / Pause", "Media"),
    PLAY("Play", "Media"),
    PAUSE("Pause", "Media"),
    SET_VOLUME("Set Volume", "Media"),
    MODIFY_VOLUME_INCREASE("Increase Volume", "Media"),
    MODIFY_VOLUME_DECREASE("Decrease Volume", "Media"),
    NEXT_TRACK("Next Track", "Media"),
    PREVIOUS_TRACK("Previous Track", "Media"),
    ANNOUNCE_TRACK("What's Playing?", "Media"),
    NC_TOGGLE("ANC ↔ Transparent", "Noise Controls"),
    NC_ACTIVE("Active Noise Cancellation", "Noise Controls"),
    NC_OFF("No Noise Controls", "Noise Controls"),
    NC_TRANSPARENT("Transparent Mode", "Noise Controls"),
    NC_ADAPTIVE("Adaptive Noise Cancellation", "Noise Controls"),
    VOICE_ASSISTANT("Voice Assistant", "System"),
    ACCEPT_CALL("Accept Call", "Calls"),
    REJECT_CALL("Reject Call", "Calls"),
    READ_NOTIFICATIONS("Read Notifications", "Notifications"),
    LAUNCH_APP("Start an Application", "System"),
    FIT_TEST("Earbud Fit Test", "System"),
    SPEAK_TEXT("Speak Out Loud", "System"),
    NO_ACTION("No Action", "System")
}
