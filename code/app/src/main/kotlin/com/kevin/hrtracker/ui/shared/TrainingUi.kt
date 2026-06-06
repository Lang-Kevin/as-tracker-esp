package com.kevin.armswing.ui.shared

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.kevin.armswing.ui.theme.LightPurple

@Composable
fun OmegaSessionChart(
    omegaHistory: List<Float>,
    currentOmega: Float?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    Canvas(modifier = modifier) {
        if (omegaHistory.size < 2) return@Canvas

        val leftPaddingPx = with(density) { 54.dp.toPx() }
        val chartWidth = size.width - leftPaddingPx

        val dataMin = omegaHistory.min()
        val dataMax = omegaHistory.max()
        val pad = ((dataMax - dataMin) * 0.1f).coerceAtLeast(0.5f)
        val yMin = dataMin - pad
        val yMax = dataMax + pad
        val yRange = (yMax - yMin).coerceAtLeast(0.01f)

        fun omegaToY(v: Float): Float = size.height * (1f - (v - yMin) / yRange)

        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 10.sp.toPx() }
            color = android.graphics.Color.argb(160, 255, 255, 255)
        }

        listOf(yMin, (yMin + yMax) / 2f, yMax).forEach { tick ->
            val y = omegaToY(tick)
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(leftPaddingPx, y),
                end = Offset(size.width, y),
                strokeWidth = with(density) { 1.dp.toPx() }
            )
            drawContext.canvas.nativeCanvas.drawText(
                "%.1f".format(tick),
                4f,
                y + labelPaint.textSize / 3f,
                labelPaint
            )
        }

        val path = Path()
        omegaHistory.forEachIndexed { index, v ->
            val x = leftPaddingPx + (index.toFloat() / (omegaHistory.size - 1)) * chartWidth
            val y = omegaToY(v.coerceIn(yMin, yMax))
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = Color.White,
            style = Stroke(
                width = with(density) { 2.dp.toPx() },
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        val lastVal = currentOmega ?: omegaHistory.last()
        val lastX = leftPaddingPx + chartWidth
        val lastY = omegaToY(lastVal.coerceIn(yMin, yMax))

        drawCircle(
            color = LightPurple,
            radius = with(density) { 4.dp.toPx() },
            center = Offset(lastX, lastY)
        )

        val valuePaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 11.sp.toPx() }
            color = LightPurple.toArgb()
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        drawContext.canvas.nativeCanvas.drawText(
            "%.2f rad/s".format(lastVal),
            size.width - with(density) { 2.dp.toPx() },
            lastY - with(density) { 8.dp.toPx() },
            valuePaint
        )
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}
