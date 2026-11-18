package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.example.data.model.DanmakuComment
import com.example.data.model.DanmakuPosition
import com.example.data.model.DanmakuSettings
import kotlin.math.abs

/**
 * High-performance Jetpack Compose Canvas danmaku renderer.
 * Supports scrolling (naka), top (ue), and bottom (shita) Niconico comments with crisp dark stroke outline.
 */
@Composable
fun DanmakuOverlay(
    comments: List<DanmakuComment>,
    currentPositionMs: Long,
    settings: DanmakuSettings,
    modifier: Modifier = Modifier
) {
    if (!settings.enabled || comments.isEmpty()) return

    val density = LocalDensity.current
    val textSizePx = with(density) { settings.fontSizeSp.sp.toPx() }

    val fillPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            style = Paint.Style.FILL
        }
    }
    val strokePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
        }
    }

    // Duration a scrolling comment takes to cross the screen
    val scrollDurationMs = (6500f / settings.speedMultiplier.coerceIn(0.5f, 2.0f)).toLong()
    val fixedDurationMs = 3500L

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        val usableHeight = height * settings.displayAreaFraction.coerceIn(0.25f, 1.0f)
        val laneHeight = textSizePx * 1.38f
        val laneCount = (usableHeight / laneHeight).toInt().coerceAtLeast(3)
        val alphaInt = (settings.opacity.coerceIn(0.1f, 1.0f) * 255).toInt()

        fillPaint.textSize = textSizePx
        strokePaint.textSize = textSizePx
        strokePaint.strokeWidth = (textSizePx * 0.14f).coerceAtLeast(2.5f)
        strokePaint.color = (alphaInt shl 24) or 0x000000

        val activeComments = comments.filter { c ->
            val dur = if (c.position == DanmakuPosition.SCROLL) scrollDurationMs else fixedDurationMs
            currentPositionMs >= c.vposMs && currentPositionMs <= c.vposMs + dur
        }.take(45)

        val nativeCanvas = drawContext.canvas.nativeCanvas
        var topLane = 0
        var bottomLane = 0

        for (comment in activeComments) {
            val elapsed = currentPositionMs - comment.vposMs
            val textWidth = fillPaint.measureText(comment.text)
            val rgb = (comment.colorHex and 0x00FFFFFFL).toInt()
            fillPaint.color = (alphaInt shl 24) or rgb

            when (comment.position) {
                DanmakuPosition.SCROLL -> {
                    val progress = elapsed.toFloat() / scrollDurationMs.toFloat()
                    val x = width - progress * (width + textWidth)
                    val lane = abs(comment.id.hashCode()) % laneCount
                    val y = (lane + 1) * laneHeight
                    nativeCanvas.drawText(comment.text, x, y, strokePaint)
                    nativeCanvas.drawText(comment.text, x, y, fillPaint)
                }

                DanmakuPosition.TOP -> {
                    val x = (width - textWidth) / 2f
                    val y = ((topLane % laneCount) + 1) * laneHeight
                    topLane++
                    nativeCanvas.drawText(comment.text, x, y, strokePaint)
                    nativeCanvas.drawText(comment.text, x, y, fillPaint)
                }

                DanmakuPosition.BOTTOM -> {
                    val x = (width - textWidth) / 2f
                    val y = height - (bottomLane % laneCount) * laneHeight - textSizePx * 0.5f
                    bottomLane++
                    nativeCanvas.drawText(comment.text, x, y, strokePaint)
                    nativeCanvas.drawText(comment.text, x, y, fillPaint)
                }
            }
        }
    }
}
