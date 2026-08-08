package com.benegedeniz.budsdynamiceq.data.model

import androidx.annotation.StringRes
import com.benegedeniz.budsdynamiceq.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.benegedeniz.budsdynamiceq.di.ServiceLocator

enum class GestureAction(@param:StringRes val displayNameRes: Int, @param:StringRes val groupRes: Int) {
    PLAY_PAUSE(R.string.action_play_pause, R.string.group_media),
    PLAY(R.string.action_play, R.string.group_media),
    PAUSE(R.string.action_pause, R.string.group_media),
    SET_VOLUME(R.string.action_set_volume, R.string.group_media),
    MODIFY_VOLUME_INCREASE(R.string.action_vol_increase, R.string.group_media),
    MODIFY_VOLUME_DECREASE(R.string.action_vol_decrease, R.string.group_media),
    NEXT_TRACK(R.string.action_next_track, R.string.group_media),
    PREVIOUS_TRACK(R.string.action_prev_track, R.string.group_media),
    ANNOUNCE_TRACK(R.string.action_announce, R.string.group_media),
    NC_TOGGLE(R.string.action_nc_toggle, R.string.group_noise),
    NC_ACTIVE(R.string.action_nc_active, R.string.group_noise),
    NC_OFF(R.string.action_nc_off, R.string.group_noise),
    NC_TRANSPARENT(R.string.action_nc_trans, R.string.group_noise),
    NC_ADAPTIVE(R.string.action_nc_adapt, R.string.group_noise),
    VOICE_ASSISTANT(R.string.action_voice_assist, R.string.group_system),
    ACCEPT_CALL(R.string.action_accept_call, R.string.group_calls),
    REJECT_CALL(R.string.action_reject_call, R.string.group_calls),
    READ_NOTIFICATIONS(R.string.action_read_notifs, R.string.group_notifs),
    LAUNCH_APP(R.string.action_launch_app, R.string.group_system),
    FIT_TEST(R.string.action_fit_test, R.string.group_system),
    SPEAK_TEXT(R.string.action_speak_text, R.string.group_system),
    NO_ACTION(R.string.action_none, R.string.group_system)
}

@Composable
fun GestureAction.getDisplayName(): String {
    val context = LocalContext.current
    val budsController = ServiceLocator.provideBudsController(context)
    val model = budsController.effectiveModel.collectAsState().value
    if (this == GestureAction.NC_TOGGLE && !model.supportsTransparencyNC) {
        return stringResource(R.string.action_nc_toggle_off)
    }
    return stringResource(this.displayNameRes)
}

fun GestureAction.getDisplayNameString(context: android.content.Context): String {
    val budsController = ServiceLocator.provideBudsController(context)
    val model = budsController.effectiveModel.value
    if (this == GestureAction.NC_TOGGLE && !model.supportsTransparencyNC) {
        return context.getString(R.string.action_nc_toggle_off)
    }
    return context.getString(this.displayNameRes)
}
