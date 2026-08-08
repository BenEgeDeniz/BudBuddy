package com.benegedeniz.budsdynamiceq.data.model

import androidx.annotation.StringRes
import com.benegedeniz.budsdynamiceq.R

enum class NoiseControlMode(@param:StringRes val displayNameRes: Int, val payloadByte: Byte) {
    DEFAULT(R.string.nc_default, -2),
    IGNORE(R.string.nc_ignore, -1),
    OFF(R.string.nc_off, 0x00),
    NOISE_CANCELLATION(R.string.nc_anc, 0x01),
    TRANSPARENT(R.string.nc_transparent, 0x02),
    ADAPTIVE(R.string.nc_adaptive, 0x03)
}
