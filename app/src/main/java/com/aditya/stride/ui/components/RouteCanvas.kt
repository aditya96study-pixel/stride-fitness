package com.aditya.stride.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max

/**
 * Lightweight route trace with no map tiles: used live during a run so tracking
 * costs nothing but GPS. The full map with tiles lives on the run detail screen.
 */
@Composable
fun RouteCanvas(
    latitudes: List<Double>,
    longitudes: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (latitudes.size < 2) {
            Text(
                "Waiting for a GPS fix…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Box
        }

        Canvas(Modifier.fillMaxSize()) {
            val latMin = latitudes.min()
            val latMax = latitudes.max()
            val lonMin = longitudes.min()
            val lonMax = longitudes.max()
            val midLat = (latMin + latMax) / 2.0

            // Longitude degrees shrink with latitude; without this correction the
            // route comes out stretched sideways.
            val lonScale = cos(Math.toRadians(midLat))
            val spanLat = max(latMax - latMin, 1e-6)
            val spanLon = max((lonMax - lonMin) * lonScale, 1e-6)

            val pad = 18f
            val usableW = size.width - pad * 2
            val usableH = size.height - pad * 2
            val scale = minOf(usableW / spanLon, usableH / spanLat).toFloat()

            val drawW = (spanLon * scale).toFloat()
            val drawH = (spanLat * scale).toFloat()
            val offsetX = pad + (usableW - drawW) / 2f
            val offsetY = pad + (usableH - drawH) / 2f

            fun project(lat: Double, lon: Double): Offset {
                val x = offsetX + ((lon - lonMin) * lonScale * scale).toFloat()
                // Screen y grows downward, latitude grows upward.
                val y = offsetY + drawH - ((lat - latMin) * scale).toFloat()
                return Offset(x, y)
            }

            val path = Path()
            latitudes.indices.forEach { i ->
                val p = project(latitudes[i], longitudes[i])
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            val start = project(latitudes.first(), longitudes.first())
            val end = project(latitudes.last(), longitudes.last())
            drawCircle(Color.White, radius = 5.5f, center = start)
            drawCircle(color, radius = 3.5f, center = start)
            drawCircle(Color.White, radius = 6.5f, center = end)
            drawCircle(color, radius = 4.5f, center = end)
        }
    }
}
