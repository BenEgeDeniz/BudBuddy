package com.benegedeniz.budsdynamiceq.ui.headshake

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.benegedeniz.budsdynamiceq.gesture.QuaternionSample
import kotlinx.coroutines.isActive
import kotlin.math.*

/**
 * Interactive 3D Head Visualizer component with Dead Reckoning SLERP interpolation.
 * Renders a stylized 3D Head mesh with earbuds and relative orientation tracking.
 */
@Composable
fun Head3DCanvas(
    sample: QuaternionSample?,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    resetTrigger: Any? = null,
    leftEarbudColor: Color? = null,
    rightEarbudColor: Color? = null
) {
    var refQuaternion by remember { mutableStateOf<QuaternionSample?>(null) }
    var currentQuaternion by remember { mutableStateOf<QuaternionSample?>(null) }

    // Reset quaternions when resetTrigger changes
    LaunchedEffect(resetTrigger) {
        refQuaternion = null
        currentQuaternion = null
    }

    // Dead Reckoning SLERP loop running at display frame rate
    LaunchedEffect(sample) {
        val target = sample ?: return@LaunchedEffect
        if (refQuaternion == null) {
            refQuaternion = target
            currentQuaternion = target
        }
        val startQ = currentQuaternion ?: target
        val startTime = System.currentTimeMillis()
        val duration = 40L // ~40ms smoothing window for dead reckoning

        while (isActive) {
            val elapsed = System.currentTimeMillis() - startTime
            val t = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            currentQuaternion = slerp(startQ, target, t)
            if (t >= 1f) break
            withFrameNanos { }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Use DrawScope's actual bounds instead of the hardcoded parameter
            val canvasWidth = this.size.width
            val canvasHeight = this.size.height
            val cx = canvasWidth / 2f
            val cy = canvasHeight / 2f
            val scaleFactor = min(canvasWidth, canvasHeight) * 0.38f

            val qCurrent = currentQuaternion
            val qRef = refQuaternion

            // Compute relative rotation qRel = qRef * qCurrent^-1 (Rotation from Ref to Current)
            val relQ = if (qCurrent != null && qRef != null) {
                val qCurrentInv = QuaternionSample(0, -qCurrent.x, -qCurrent.y, -qCurrent.z, qCurrent.w)
                val qRel = multiplyQuat(qRef, qCurrentInv)
                
                // Android IMU axes -> Screen Mirror axes mapping
                // Negate Pitch (X) and Yaw (Y) for mirror effect, keep Roll (Z) as is.
                QuaternionSample(0, -qRel.x, -qRel.y, qRel.z, qRel.w)
            } else {
                QuaternionSample(0, 0f, 0f, 0f, 1f)
            }

            // Draw background reference grid circle
            drawCircle(
                color = surfaceVariant,
                radius = scaleFactor * 1.1f,
                center = Offset(cx, cy),
                style = Stroke(width = 2f)
            )

            // Define 3D Mesh vertices for Head
            // Latitude rings (Horizontal head contours)
            val rings = listOf(-0.6f, -0.3f, 0.0f, 0.3f, 0.6f)
            for (yLevel in rings) {
                val ringRadius = sqrt(max(0f, 1.0f - yLevel * yLevel))
                val ringPath = Path()
                var first = true
                val segments = 24
                for (i in 0..segments) {
                    val angle = (i.toFloat() / segments) * 2f * PI.toFloat()
                    val vx = ringRadius * cos(angle)
                    val vy = yLevel
                    val vz = ringRadius * sin(angle)

                    val p = project3D(vx, vy, vz, relQ, cx, cy, scaleFactor)
                    if (first) {
                        ringPath.moveTo(p.x, p.y)
                        first = false
                    } else {
                        ringPath.lineTo(p.x, p.y)
                    }
                }
                drawPath(
                    path = ringPath,
                    color = primaryColor.copy(alpha = 0.25f),
                    style = Stroke(width = 1.5f)
                )
            }

            // Longitude lines (Vertical head contours)
            val longitudes = listOf(0f, PI.toFloat() / 2, PI.toFloat(), 3 * PI.toFloat() / 2)
            for (longAngle in longitudes) {
                val longPath = Path()
                var first = true
                val segments = 24
                for (i in 0..segments) {
                    val latAngle = (i.toFloat() / segments) * PI.toFloat() - (PI.toFloat() / 2)
                    val vy = sin(latAngle)
                    val r = cos(latAngle)
                    val vx = r * cos(longAngle)
                    val vz = r * sin(longAngle)

                    val p = project3D(vx, vy, vz, relQ, cx, cy, scaleFactor)
                    if (first) {
                        longPath.moveTo(p.x, p.y)
                        first = false
                    } else {
                        longPath.lineTo(p.x, p.y)
                    }
                }
                drawPath(
                    path = longPath,
                    color = primaryColor.copy(alpha = 0.3f),
                    style = Stroke(width = 1.5f)
                )
            }

            // Left Eye
            val leftEyeP = project3D(-0.35f, 0.3f, 0.88f, relQ, cx, cy, scaleFactor)
            drawCircle(
                color = secondaryColor,
                radius = 12.dp.toPx(),
                center = Offset(leftEyeP.x, leftEyeP.y)
            )

            // Right Eye
            val rightEyeP = project3D(0.35f, 0.3f, 0.88f, relQ, cx, cy, scaleFactor)
            drawCircle(
                color = secondaryColor,
                radius = 12.dp.toPx(),
                center = Offset(rightEyeP.x, rightEyeP.y)
            )

            // Smile / Mouth
            val mouthPath = Path()
            val mouthPoints = listOf(
                floatArrayOf(-0.3f, -0.3f, 0.9f),
                floatArrayOf(-0.15f, -0.4f, 0.94f),
                floatArrayOf(0.0f, -0.45f, 0.95f),
                floatArrayOf(0.15f, -0.4f, 0.94f),
                floatArrayOf(0.3f, -0.3f, 0.9f)
            )
            var mouthFirst = true
            for (mp in mouthPoints) {
                val p = project3D(mp[0], mp[1], mp[2], relQ, cx, cy, scaleFactor)
                if (mouthFirst) {
                    mouthPath.moveTo(p.x, p.y)
                    mouthFirst = false
                } else {
                    mouthPath.lineTo(p.x, p.y)
                }
            }
            drawPath(
                path = mouthPath,
                color = secondaryColor,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )

            // Earbuds 3D Features (Left & Right Galaxy Buds)
            val leftBud = floatArrayOf(-1.05f, 0.05f, 0.1f)
            val rightBud = floatArrayOf(1.05f, 0.05f, 0.1f)

            val pLeft = project3D(leftBud[0], leftBud[1], leftBud[2], relQ, cx, cy, scaleFactor)
            val pRight = project3D(rightBud[0], rightBud[1], rightBud[2], relQ, cx, cy, scaleFactor)

            drawCircle(
                color = rightEarbudColor ?: primaryColor, // Right ear is on the left side of the screen since head faces us
                radius = 7.dp.toPx(),
                center = Offset(pLeft.x, pLeft.y)
            )
            drawCircle(
                color = leftEarbudColor ?: primaryColor, // Left ear is on the right side of the screen since head faces us
                radius = 7.dp.toPx(),
                center = Offset(pRight.x, pRight.y)
            )
        }
    }
}

private fun project3D(
    x: Float, y: Float, z: Float,
    q: QuaternionSample,
    cx: Float, cy: Float,
    scale: Float
): Offset {
    // Quaternion rotation: q * v * q^-1
    val ix = q.w * x + q.y * z - q.z * y
    val iy = q.w * y + q.z * x - q.x * z
    val iz = q.w * z + q.x * y - q.y * x
    val iw = -q.x * x - q.y * y - q.z * z

    val rx = ix * q.w + iw * -q.x + iy * -q.z - iz * -q.y
    val ry = iy * q.w + iw * -q.y + iz * -q.x - ix * -q.z
    val rz = iz * q.w + iw * -q.z + ix * -q.y - iy * -q.x

    // Simple perspective projection
    val fov = 400f
    val distance = 300f
    val zScale = fov / (distance + rz * 80f)

    val px = cx + rx * scale * zScale
    val py = cy - ry * scale * zScale // Y inverted for screen coordinates

    return Offset(px, py)
}

private fun multiplyQuat(q1: QuaternionSample, q2: QuaternionSample): QuaternionSample {
    val x = q1.w * q2.x + q1.x * q2.w + q1.y * q2.z - q1.z * q2.y
    val y = q1.w * q2.y - q1.x * q2.z + q1.y * q2.w + q1.z * q2.x
    val z = q1.w * q2.z + q1.x * q2.y - q1.y * q2.x + q1.z * q2.w
    val w = q1.w * q2.w - q1.x * q2.x - q1.y * q2.y - q1.z * q2.z
    return QuaternionSample(q2.timestampMs, x, y, z, w)
}

private fun slerp(q1: QuaternionSample, q2: QuaternionSample, t: Float): QuaternionSample {
    var cosHalfTheta = q1.w * q2.w + q1.x * q2.x + q1.y * q2.y + q1.z * q2.z
    var q2x = q2.x
    var q2y = q2.y
    var q2z = q2.z
    var q2w = q2.w

    if (cosHalfTheta < 0) {
        q2w = -q2w
        q2x = -q2x
        q2y = -q2y
        q2z = -q2z
        cosHalfTheta = -cosHalfTheta
    }

    if (abs(cosHalfTheta) >= 1.0f) {
        return q1
    }

    val halfTheta = acos(cosHalfTheta.coerceIn(-1f, 1f))
    val sinHalfTheta = sqrt(max(0f, 1.0f - cosHalfTheta * cosHalfTheta))

    if (abs(sinHalfTheta) < 0.001f) {
        return QuaternionSample(
            q2.timestampMs,
            q1.x * 0.5f + q2x * 0.5f,
            q1.y * 0.5f + q2y * 0.5f,
            q1.z * 0.5f + q2z * 0.5f,
            q1.w * 0.5f + q2w * 0.5f
        )
    }

    val ratioA = sin((1 - t) * halfTheta) / sinHalfTheta
    val ratioB = sin(t * halfTheta) / sinHalfTheta

    return QuaternionSample(
        q2.timestampMs,
        q1.x * ratioA + q2x * ratioB,
        q1.y * ratioA + q2y * ratioB,
        q1.z * ratioA + q2z * ratioB,
        q1.w * ratioA + q2w * ratioB
    )
}
