package com.benegedeniz.budsdynamiceq.gesture

import kotlinx.serialization.Serializable
import kotlin.math.*

/**
 * A compact statistical fingerprint of a noise pattern (walking, bus, running, etc.).
 * Computed from recorded IMU samples and used for continuous sliding-window matching
 * instead of DTW shape matching.
 */
@Serializable
data class NoiseFingerprint(
    /** Mean angular velocity magnitude across all windows (radians/s in quaternion space) */
    val meanAngularVelocity: Float,
    /** Dominant frequency in Hz estimated via zero-crossing rate */
    val dominantFrequency: Float,
    /** Normalized axis distribution [x, y, z] — which axes carry the most movement */
    val axisDistribution: List<Float>,
    /** Variance of angular velocity magnitude across windows — how consistent the noise is */
    val velocityVariance: Float,
    /** Amplitude range [min, max] of per-window peak displacement */
    val amplitudeMin: Float,
    val amplitudeMax: Float
) {
    /**
     * Weighted Euclidean distance to another fingerprint.
     * Lower = more similar. Returns a value in [0, ∞).
     */
    fun distanceTo(other: NoiseFingerprint): Float {
        // Velocity magnitude similarity (most important — walking vs. still)
        val velDiff = (meanAngularVelocity - other.meanAngularVelocity)
        val velNorm = max(meanAngularVelocity, other.meanAngularVelocity).coerceAtLeast(0.01f)
        val velScore = (velDiff / velNorm).let { it * it } * 3.0f

        // Frequency similarity (walking ~2Hz, running ~3Hz, bus ~0.5Hz)
        val freqDiff = (dominantFrequency - other.dominantFrequency)
        val freqNorm = max(dominantFrequency, other.dominantFrequency).coerceAtLeast(0.1f)
        val freqScore = (freqDiff / freqNorm).let { it * it } * 2.0f

        // Axis distribution similarity (dot product — 1.0 = identical axes)
        val axisDot = (0 until 3).sumOf {
            (axisDistribution[it] * other.axisDistribution[it]).toDouble()
        }.toFloat()
        val axisScore = (1.0f - axisDot.coerceIn(0f, 1f)) * 2.0f

        // Variance similarity
        val varDiff = abs(velocityVariance - other.velocityVariance)
        val varNorm = max(velocityVariance, other.velocityVariance).coerceAtLeast(0.001f)
        val varScore = (varDiff / varNorm) * 1.0f

        // Amplitude similarity
        val liveAmp = other.amplitudeMax // Live fingerprint stores its amplitude here
        val ampScore = if (amplitudeMax > 0f) {
            if (liveAmp < amplitudeMin * 0.5f) { // 2x weaker
                ((amplitudeMin * 0.5f - liveAmp) / (amplitudeMin * 0.5f)) * 15.0f
            } else if (liveAmp > amplitudeMax * 2.0f) { // 2x stronger
                ((liveAmp - amplitudeMax * 2.0f) / (amplitudeMax * 2.0f)) * 15.0f
            } else {
                0f
            }
        } else 0f

        return velScore + freqScore + axisScore + varScore + ampScore
    }

    companion object {
        /**
         * Compute a noise fingerprint from a set of raw quaternion samples.
         * The samples should cover several seconds of continuous movement.
         */
        fun compute(samples: List<QuaternionSample>): NoiseFingerprint? {
            if (samples.size < 10) return null

            // Compute frame-to-frame angular velocities
            val angularVelocities = mutableListOf<FloatArray>() // [magX, magY, magZ, totalMag]
            for (i in 1 until samples.size) {
                val prev = samples[i - 1]
                val curr = samples[i]
                val dt = (curr.timestampMs - prev.timestampMs) / 1000f
                if (dt <= 0f || dt > 0.5f) continue // skip bad deltas

                val dx = (curr.x - prev.x) / dt
                val dy = (curr.y - prev.y) / dt
                val dz = (curr.z - prev.z) / dt
                val mag = sqrt(dx * dx + dy * dy + dz * dz)
                angularVelocities.add(floatArrayOf(abs(dx), abs(dy), abs(dz), mag))
            }

            if (angularVelocities.size < 5) return null

            // Mean angular velocity
            val meanVel = angularVelocities.map { it[3] }.average().toFloat()

            // Velocity variance
            val velVariance = angularVelocities.map { it[3] }.let { mags ->
                val mean = mags.average().toFloat()
                mags.map { (it - mean).let { d -> d * d } }.average().toFloat()
            }

            // Axis distribution (normalized)
            var sumX = 0f; var sumY = 0f; var sumZ = 0f
            for (av in angularVelocities) {
                sumX += av[0]; sumY += av[1]; sumZ += av[2]
            }
            val axisMag = sqrt(sumX * sumX + sumY * sumY + sumZ * sumZ).coerceAtLeast(0.001f)
            val axisDistribution = listOf(sumX / axisMag, sumY / axisMag, sumZ / axisMag)

            // Dominant frequency via zero-crossing rate on the magnitude signal
            val magnitudes = angularVelocities.map { it[3] }
            val meanMag = magnitudes.average().toFloat()
            var zeroCrossings = 0
            for (i in 1 until magnitudes.size) {
                if ((magnitudes[i] - meanMag) * (magnitudes[i - 1] - meanMag) < 0) {
                    zeroCrossings++
                }
            }
            val totalDurationS = (samples.last().timestampMs - samples.first().timestampMs) / 1000f
            // Zero-crossing rate gives ~2x the frequency
            val dominantFreq = if (totalDurationS > 0) (zeroCrossings / 2f) / totalDurationS else 0f

            // Amplitude range from 1-second windows
            val windowAmps = mutableListOf<Float>()
            val windowSizeMs = 1000L
            val stepMs = 500L
            var windowStart = samples.first().timestampMs
            while (windowStart + windowSizeMs <= samples.last().timestampMs) {
                val windowEnd = windowStart + windowSizeMs
                val windowSamples = samples.filter { it.timestampMs in windowStart..windowEnd }
                if (windowSamples.size >= 5) {
                    windowAmps.add(DtwEngine.getRawAmplitude(windowSamples))
                }
                windowStart += stepMs
            }

            val ampMin = windowAmps.minOrNull() ?: 0f
            val ampMax = windowAmps.maxOrNull() ?: 0f

            return NoiseFingerprint(
                meanAngularVelocity = meanVel,
                dominantFrequency = dominantFreq,
                axisDistribution = axisDistribution,
                velocityVariance = velVariance,
                amplitudeMin = ampMin,
                amplitudeMax = ampMax
            )
        }

        /**
         * Compute a fingerprint from a short sliding window of live data.
         * Same computation as [compute], optimized for a smaller sample set.
         */
        fun computeFromWindow(samples: List<QuaternionSample>): NoiseFingerprint? {
            if (samples.size < 5) return null

            val angularVelocities = mutableListOf<FloatArray>()
            for (i in 1 until samples.size) {
                val prev = samples[i - 1]
                val curr = samples[i]
                val dt = (curr.timestampMs - prev.timestampMs) / 1000f
                if (dt <= 0f || dt > 0.5f) continue
                
                val dx = (curr.x - prev.x) / dt
                val dy = (curr.y - prev.y) / dt
                val dz = (curr.z - prev.z) / dt
                val mag = sqrt(dx * dx + dy * dy + dz * dz)
                angularVelocities.add(floatArrayOf(abs(dx), abs(dy), abs(dz), mag))
            }

            if (angularVelocities.size < 3) return null

            val meanVel = angularVelocities.map { it[3] }.average().toFloat()
            val velVariance = angularVelocities.map { it[3] }.let { mags ->
                val mean = mags.average().toFloat()
                mags.map { (it - mean).let { d -> d * d } }.average().toFloat()
            }

            var sumX = 0f; var sumY = 0f; var sumZ = 0f
            for (av in angularVelocities) {
                sumX += av[0]; sumY += av[1]; sumZ += av[2]
            }
            val axisMag = sqrt(sumX * sumX + sumY * sumY + sumZ * sumZ).coerceAtLeast(0.001f)
            val axisDistribution = listOf(sumX / axisMag, sumY / axisMag, sumZ / axisMag)

            val magnitudes = angularVelocities.map { it[3] }
            val meanMag = magnitudes.average().toFloat()
            var zeroCrossings = 0
            for (i in 1 until magnitudes.size) {
                if ((magnitudes[i] - meanMag) * (magnitudes[i - 1] - meanMag) < 0) {
                    zeroCrossings++
                }
            }
            val totalDurationS = (samples.last().timestampMs - samples.first().timestampMs) / 1000f
            val dominantFreq = if (totalDurationS > 0) (zeroCrossings / 2f) / totalDurationS else 0f

            val liveAmp = DtwEngine.getRawAmplitude(samples)

            return NoiseFingerprint(
                meanAngularVelocity = meanVel,
                dominantFrequency = dominantFreq,
                axisDistribution = axisDistribution,
                velocityVariance = velVariance,
                amplitudeMin = liveAmp,
                amplitudeMax = liveAmp
            )
        }
    }
}
