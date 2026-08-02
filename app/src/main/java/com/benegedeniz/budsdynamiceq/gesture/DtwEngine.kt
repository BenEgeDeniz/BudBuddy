package com.benegedeniz.budsdynamiceq.gesture

import android.util.Log
import com.benegedeniz.budsdynamiceq.data.model.HeadGesture
import kotlin.math.*

object DtwEngine {

    private const val TAG = "DtwEngine"
    const val DETECTION_THRESHOLD = 0.50f
    const val AMBIGUITY_RATIO = 0.80f

    fun preprocess(samples: List<QuaternionSample>): List<FloatArray> {
        if (samples.size < 3) return emptyList()

        // 1. Compute frame-to-frame delta quaternions in the BODY frame: dq = q[i] * q[i-1]^-1
        //    With world-to-device quaternions, this multiplication order expresses the delta
        //    in the device's local coordinate frame. Pitch is always pitch, yaw is always yaw,
        //    regardless of which direction the user is facing in the world.
        val deltas = mutableListOf<FloatArray>()
        for (i in 1 until samples.size) {
            val prevInv = inverse(samples[i - 1])
            val dq = multiply(samples[i], prevInv)
            
            // Ensure w > 0 for consistent sign (double-cover fix)
            var rx = dq.x
            var ry = dq.y
            var rz = dq.z
            if (dq.w < 0) {
                rx = -rx
                ry = -ry
                rz = -rz
            }
            deltas.add(floatArrayOf(rx, ry, rz))
        }

        // 2. Smooth the deltas to remove packet jitter
        val smoothedDeltas = smooth(deltas)

        // 3. Integrate (cumulative sum) to get displacement curve.
        //    DTW works best on displacement curves rather than velocity signals
        //    because it can handle time warping of the shape.
        val integrated = mutableListOf<FloatArray>()
        var cx = 0f; var cy = 0f; var cz = 0f
        integrated.add(floatArrayOf(0f, 0f, 0f))
        for (d in smoothedDeltas) {
            cx += d[0]
            cy += d[1]
            cz += d[2]
            integrated.add(floatArrayOf(cx, cy, cz))
        }

        // 4. Resample to 50 points
        val resampled = resample(integrated, 50)

        // 5. Enforce minimum amplitude threshold (rejects tiny twitches)
        val filtered = enforceMinimumMovement(resampled)
        if (filtered.isEmpty()) return emptyList()

        // 6. Amplitude-normalize so DTW compares shape only, not scale.
        //    A small circle and a big circle produce the same normalized curve.
        val amp = getMaxAmplitude(filtered)
        if (amp < 0.001f) return emptyList()
        return filtered.map { floatArrayOf(it[0] / amp, it[1] / amp, it[2] / amp) }
    }

    private fun getDuration(samples: List<QuaternionSample>): Long {
        if (samples.isEmpty()) return 0L
        return samples.last().timestampMs - samples.first().timestampMs
    }

    fun computeDtw(
        template: List<FloatArray>, 
        live: List<FloatArray>,
        templateDuration: Long = 0L,
        liveDuration: Long = 0L,
        templateAmp: Float = 0f,
        liveAmp: Float = 0f
    ): Float {
        if (template.isEmpty() || live.isEmpty()) return Float.MAX_VALUE

        // 1. Spatial Axis Alignment Check
        // Ensure the two gestures share the same dominant axis of movement (e.g. both are Pitch-heavy).
        // If one is purely X (Nod) and the other is purely Y (Shake), their dot product is 0.
        val templateAxis = getAxisDistribution(template)
        val liveAxis = getAxisDistribution(live)
        val axisDot = templateAxis[0]*liveAxis[0] + templateAxis[1]*liveAxis[1] + templateAxis[2]*liveAxis[2]
        if (axisDot < 0.70f) {
            return Float.MAX_VALUE
        }

        val n = template.size
        val m = live.size
        val window = max(n, m) / 3 // 33% Sakoe-Chiba band to allow more time-warping for circles

        val dtw = Array(n + 1) { FloatArray(m + 1) { Float.MAX_VALUE } }
        dtw[0][0] = 0f

        for (i in 1..n) {
            val startJ = max(1, i - window)
            val endJ = min(m, i + window)
            for (j in startJ..endJ) {
                val cost = distance(template[i - 1], live[j - 1])
                dtw[i][j] = cost + min(dtw[i - 1][j], min(dtw[i][j - 1], dtw[i - 1][j - 1]))
            }
        }
        
        val rawDtw = dtw[n][m] / max(n, m)
        
        val templateNPL = calculate3DNPL(template)
        val liveNPL = calculate3DNPL(live)
        
        val nplRatio = min(templateNPL, liveNPL) / max(templateNPL, liveNPL)
        
        // Structural Complexity Check (1D Projected Normalized Path Length)
        // 1D projection ensures 2D gestures (like circles) are not rejected due to natural elliptical variance.
        // A single movement has NPL ~2.0. A double movement has NPL ~4.0.
        // If NPL differs heavily (ratio < 0.55), they are structurally different (single vs double)
        if (nplRatio < 0.55f) {
            return Float.MAX_VALUE
        }
        
        val penalty = if (nplRatio < 0.70f) {
            // Scale penalty smoothly to give some leeway for sloppy gestures
            1.0f + 4.0f * (0.70f - nplRatio)
        } else {
            1.0f
        }
        
        var durationPenalty = 1.0f
        if (templateDuration > 0 && liveDuration > 0) {
            val ratio = min(templateDuration, liveDuration).toFloat() / max(templateDuration, liveDuration)
            if (ratio < 0.3f) {
                // If it takes more than 3.3x the time, it's structurally completely different speed
                return Float.MAX_VALUE
            }
            if (ratio < 0.6f) {
                durationPenalty = 1.0f + 3.0f * (0.6f - ratio)
            }
        }
        
        var ampPenalty = 1.0f
        if (templateAmp > 0 && liveAmp > 0) {
            // Only penalize if the live gesture is much SMALLER than the template.
            // If the user does a gesture larger than recorded, that's fine.
            if (liveAmp < templateAmp * 0.35f) {
                // The live motion is less than 35% the size of the recorded gesture. Likely an accidental twitch!
                return Float.MAX_VALUE
            }
            if (liveAmp < templateAmp * 0.60f) {
                // Between 35% and 60%, apply a smooth penalty
                val shortage = (templateAmp * 0.60f) - liveAmp
                ampPenalty = 1.0f + (shortage / (templateAmp * 0.25f)) * 2.0f
            }
        }

        val finalDist = rawDtw * penalty * durationPenalty * ampPenalty
        android.util.Log.d("DtwEngine", "computeDtw: raw=$rawDtw, nplRatio=$nplRatio (pen=$penalty), durPen=$durationPenalty, ampPen=$ampPenalty -> final=$finalDist")
        return finalDist
    }

    fun getRawAmplitude(samples: List<QuaternionSample>): Float {
        if (samples.size < 3) return 0f
        val deltas = mutableListOf<FloatArray>()
        for (i in 1 until samples.size) {
            val prevInv = inverse(samples[i - 1])
            val dq = multiply(prevInv, samples[i])
            var rx = dq.x; var ry = dq.y; var rz = dq.z
            if (dq.w < 0) { rx = -rx; ry = -ry; rz = -rz }
            deltas.add(floatArrayOf(rx, ry, rz))
        }
        val smoothedDeltas = smooth(deltas)
        val integrated = mutableListOf<FloatArray>()
        var cx = 0f; var cy = 0f; var cz = 0f
        integrated.add(floatArrayOf(0f, 0f, 0f))
        for (d in smoothedDeltas) {
            cx += d[0]; cy += d[1]; cz += d[2]
            integrated.add(floatArrayOf(cx, cy, cz))
        }
        return getMaxAmplitude(integrated)
    }

    fun findBestMatch(liveSamples: List<QuaternionSample>, gestures: List<HeadGesture>): HeadGesture? {
        val liveFeatures = preprocess(liveSamples)
        if (liveFeatures.isEmpty()) return null
        
        val liveDuration = getDuration(liveSamples)
        val liveAmp = getRawAmplitude(liveSamples)
        
        var bestGesture: HeadGesture? = null
        var bestDist = Float.MAX_VALUE
        var secondBestDist = Float.MAX_VALUE

        for (gesture in gestures) {
            val distancesFromRaw = gesture.templates.map { 
                computeDtw(preprocess(it), liveFeatures, getDuration(it), liveDuration, getRawAmplitude(it), liveAmp) 
            }
            val sortedDistances = distancesFromRaw.sorted()
            // Standard 1-NN DTW: Use MINIMUM distance across recorded templates!
            val gestureMinDist = if (sortedDistances.isNotEmpty()) sortedDistances.first() else Float.MAX_VALUE
            
            if (gestureMinDist < bestDist) {
                secondBestDist = bestDist
                bestDist = gestureMinDist
                bestGesture = gesture
            } else if (gestureMinDist < secondBestDist) {
                secondBestDist = gestureMinDist
            }
        }

        Log.d(TAG, "findBestMatch: bestDist=$bestDist, secondBest=$secondBestDist, threshold=$DETECTION_THRESHOLD, gesture=${bestGesture?.name}")

        if (bestDist < DETECTION_THRESHOLD) {
            if (gestures.size > 1 && secondBestDist < Float.MAX_VALUE) {
                if ((bestDist / secondBestDist) <= AMBIGUITY_RATIO) {
                    Log.i(TAG, "Match: ${bestGesture?.name} dist=$bestDist")
                    return bestGesture
                }
                Log.d(TAG, "Rejected: ambiguous ratio=${bestDist / secondBestDist}")
            } else {
                Log.i(TAG, "Match: ${bestGesture?.name} dist=$bestDist")
                return bestGesture
            }
        }
        return null
    }
    
    data class ConflictResult(
        val hasConflict: Boolean,
        val conflictingGesture: HeadGesture?,
        val similarity: Float
    )
    
    fun checkConflict(newTemplates: List<List<QuaternionSample>>, existingGestures: List<HeadGesture>): ConflictResult {
        if (newTemplates.isEmpty()) return ConflictResult(false, null, 0f)
        
        val newFeatures = newTemplates.map { preprocess(it) }
        val newDurations = newTemplates.map { getDuration(it) }
        
        var worstConflict: HeadGesture? = null
        var minDistance = Float.MAX_VALUE
        
        for (gesture in existingGestures) {
            val existingFeatures = gesture.templates.map { preprocess(it) }
            
            // Compare each new template to each existing template
            for (i in newFeatures.indices) {
                val newF = newFeatures[i]
                val newDur = newDurations[i]
                val newAmp = getRawAmplitude(newTemplates[i])
                for (j in existingFeatures.indices) {
                    val exF = existingFeatures[j]
                    val exDur = getDuration(gesture.templates[j])
                    val exAmp = getRawAmplitude(gesture.templates[j])
                    val d = computeDtw(newF, exF, newDur, exDur, newAmp, exAmp)
                    if (d < minDistance) {
                        minDistance = d
                        worstConflict = gesture
                    }
                }
            }
        }
        
        // Convert distance to similarity (0 to 1)
        val similarity = max(0f, 1f - (minDistance / (DETECTION_THRESHOLD * 2f)))
        
        return ConflictResult(
            hasConflict = similarity > 0.8f,
            conflictingGesture = if (similarity > 0.8f) worstConflict else null,
            similarity = similarity
        )
    }

    private fun inverse(q: QuaternionSample): QuaternionSample {
        val normSq = q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w
        return QuaternionSample(q.timestampMs, -q.x / normSq, -q.y / normSq, -q.z / normSq, q.w / normSq)
    }

    private fun multiply(q1: QuaternionSample, q2: QuaternionSample): QuaternionSample {
        val x = q1.w * q2.x + q1.x * q2.w + q1.y * q2.z - q1.z * q2.y
        val y = q1.w * q2.y - q1.x * q2.z + q1.y * q2.w + q1.z * q2.x
        val z = q1.w * q2.z + q1.x * q2.y - q1.y * q2.x + q1.z * q2.w
        val w = q1.w * q2.w - q1.x * q2.x - q1.y * q2.y - q1.z * q2.z
        return QuaternionSample(q2.timestampMs, x, y, z, w)
    }

    private fun quaternionToEuler(q: QuaternionSample): FloatArray {
        // Roll (x-axis rotation)
        val sinr_cosp = 2 * (q.w * q.x + q.y * q.z)
        val cosr_cosp = 1 - 2 * (q.x * q.x + q.y * q.y)
        val roll = atan2(sinr_cosp, cosr_cosp)

        // Pitch (y-axis rotation)
        val sinp = 2 * (q.w * q.y - q.z * q.x)
        val pitch = when {
            abs(sinp) >= 1 -> (PI.toFloat() / 2).withSign(sinp)
            else -> asin(sinp)
        }

        // Yaw (z-axis rotation)
        val siny_cosp = 2 * (q.w * q.z + q.x * q.y)
        val cosy_cosp = 1 - 2 * (q.y * q.y + q.z * q.z)
        val yaw = atan2(siny_cosp, cosy_cosp)

        return floatArrayOf(pitch, yaw, roll)
    }

    private fun resample(series: List<FloatArray>, targetLength: Int): List<FloatArray> {
        if (series.size <= 1) return List(targetLength) { floatArrayOf(0f, 0f, 0f) }
        
        val resampled = mutableListOf<FloatArray>()
        val ratio = (series.size - 1).toFloat() / (targetLength - 1)
        
        for (i in 0 until targetLength) {
            val idx = i * ratio
            val floor = idx.toInt()
            val ceil = min(floor + 1, series.size - 1)
            val frac = idx - floor
            
            val vFloor = series[floor]
            val vCeil = series[ceil]
            
            resampled.add(floatArrayOf(
                vFloor[0] + (vCeil[0] - vFloor[0]) * frac,
                vFloor[1] + (vCeil[1] - vFloor[1]) * frac,
                vFloor[2] + (vCeil[2] - vFloor[2]) * frac
            ))
        }
        return resampled
    }

    private fun smooth(series: List<FloatArray>): List<FloatArray> {
        if (series.size < 3) return series
        val result = mutableListOf<FloatArray>()
        result.add(series.first())
        for (i in 1 until series.size - 1) {
            val prev = series[i - 1]
            val curr = series[i]
            val next = series[i + 1]
            result.add(floatArrayOf(
                (prev[0] + curr[0] + next[0]) / 3f,
                (prev[1] + curr[1] + next[1]) / 3f,
                (prev[2] + curr[2] + next[2]) / 3f
            ))
        }
        result.add(series.last())
        return result
    }

    private fun enforceMinimumMovement(series: List<FloatArray>): List<FloatArray> {
        if (series.isEmpty()) return emptyList()

        var maxAbs = 0f
        for (p in series) {
            maxAbs = max(maxAbs, abs(p[0]))
            maxAbs = max(maxAbs, abs(p[1]))
            maxAbs = max(maxAbs, abs(p[2]))
        }
        
        // Minimum gesture movement threshold: ~9 degrees (quaternion vector mag 0.08f)
        // Discard slight nudges, twitches, and small head bobs
        if (maxAbs < 0.08f) {
            return emptyList()
        }
        
        return series
    }

    private fun distance(p1: FloatArray, p2: FloatArray): Float {
        val dx = p1[0] - p2[0]
        val dy = p1[1] - p2[1]
        val dz = p1[2] - p2[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun calculate3DNPL(series: List<FloatArray>): Float {
        var pathLen = 0f
        for (i in 1 until series.size) {
            pathLen += distance(series[i], series[i - 1])
        }
        val maxAmp = getMaxAmplitude(series)
        return pathLen / max(maxAmp, 0.001f)
    }

    private fun getMaxAmplitude(series: List<FloatArray>): Float {
        var maxAmp = 0f
        for (p in series) {
            val mag = sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2])
            maxAmp = max(maxAmp, mag)
        }
        return maxAmp
    }

    private fun getAxisDistribution(series: List<FloatArray>): FloatArray {
        var sumX = 0f; var sumY = 0f; var sumZ = 0f
        for (i in 1 until series.size) {
            sumX += abs(series[i][0] - series[i - 1][0])
            sumY += abs(series[i][1] - series[i - 1][1])
            sumZ += abs(series[i][2] - series[i - 1][2])
        }
        val mag = sqrt(sumX * sumX + sumY * sumY + sumZ * sumZ)
        if (mag < 0.0001f) return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf(sumX / mag, sumY / mag, sumZ / mag)
    }
}
