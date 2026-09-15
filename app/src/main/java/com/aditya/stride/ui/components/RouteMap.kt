package com.aditya.stride.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

/**
 * OpenStreetMap route view. OSM needs no API key and no Google account, and the
 * map only appears on this screen, so no tiles are fetched while you are running.
 */
@Composable
fun RouteMap(
    latitudes: List<Double>,
    longitudes: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    DisposableEffect(Unit) {
        onDispose { mapView.onDetach() }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
    ) {
        AndroidView(
            factory = {
                mapView.apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    setUseDataConnection(true)
                    isTilesScaledToDpi = true
                    zoomController.setVisibility(
                        org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            update = { map ->
                map.overlays.clear()
                if (latitudes.size >= 2) {
                    val points = latitudes.indices.map { GeoPoint(latitudes[it], longitudes[it]) }
                    val line = Polyline(map).apply {
                        setPoints(points)
                        outlinePaint.color = lineColor.toArgb()
                        outlinePaint.strokeWidth = 9f
                        outlinePaint.isAntiAlias = true
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                    }
                    map.overlays.add(line)

                    val startEnd = Polyline(map).apply {
                        setPoints(listOf(points.first(), points.first()))
                        outlinePaint.color = AndroidColor.WHITE
                        outlinePaint.strokeWidth = 16f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    }
                    map.overlays.add(startEnd)

                    val bounds = BoundingBox.fromGeoPoints(points)
                    map.post {
                        runCatching { map.zoomToBoundingBox(bounds, false, 80) }
                    }
                }
                map.invalidate()
            },
        )
    }
}
