package com.aditya.stride.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aditya.stride.ui.theme.seriesPalette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val Y_GUTTER = 44.dp
private val X_AXIS_HEIGHT = 22.dp

/**
 * The chart used everywhere in the app.
 *
 * Gestures, chosen so that reading a value and changing the view never fight
 * each other:
 *   - touch and hold: a crosshair snaps to the nearest day and a bubble shows the
 *     exact value; slide the finger sideways to scan across days
 *   - flick sideways: pans the window
 *   - pinch: zooms the time axis around the point between the fingers
 *   - double tap: back to the default window
 *
 * Missing days are shown honestly: the line is dashed across any gap longer than
 * two days rather than implying the trend continued through it.
 */
@Composable
fun ZoomableTimeChart(
    series: List<ChartSeries>,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    unitSuffix: String = "",
    guide: ChartGuide? = null,
    zeroBased: Boolean = false,
    chartHeight: Dp = 240.dp,
    /** Days shown when the chart first appears, and after a double tap. */
    defaultWindowDays: Float = 30f,
    emptyMessage: String = "No entries yet",
) {
    val allPoints = remember(series) { series.flatMap { it.points } }

    if (allPoints.isEmpty()) {
        Box(
            modifier
                .fillMaxWidth()
                .height(chartHeight)
                .clip(RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    ChartBody(
        series = series,
        allPoints = allPoints,
        valueLabel = valueLabel,
        modifier = modifier,
        unitSuffix = unitSuffix,
        guide = guide,
        zeroBased = zeroBased,
        chartHeight = chartHeight,
        defaultWindowDays = defaultWindowDays,
    )
}

@Composable
private fun ChartBody(
    series: List<ChartSeries>,
    allPoints: List<ChartPoint>,
    valueLabel: (Double) -> String,
    modifier: Modifier,
    unitSuffix: String,
    guide: ChartGuide?,
    zeroBased: Boolean,
    chartHeight: Dp,
    defaultWindowDays: Float,
) {
    val domainMin = remember(allPoints) { allPoints.minOf { it.epochDay }.toFloat() - 0.5f }
    val domainMax = remember(allPoints) { allPoints.maxOf { it.epochDay }.toFloat() + 0.5f }
    val fullSpan = max(domainMax - domainMin, 6f)
    val minSpan = 2.2f
    val initialSpan = min(fullSpan, defaultWindowDays)

    // Days present in any series, sorted — the crosshair snaps to these.
    val dayIndex = remember(allPoints) { allPoints.map { it.epochDay }.distinct().sorted() }

    var spanDays by remember(domainMin, domainMax) { mutableFloatStateOf(initialSpan) }
    var startDay by remember(domainMin, domainMax) {
        mutableFloatStateOf(max(domainMin, domainMax - initialSpan))
    }
    var inspectX by remember { mutableStateOf<Float?>(null) }

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val palette = seriesPalette

    val axisLabelStyle = TextStyle(fontSize = 10.5.sp, color = colors.onSurfaceVariant)
    val tooltipTitleStyle = TextStyle(
        fontSize = 11.sp,
        color = colors.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
    )
    val tooltipValueStyle = TextStyle(
        fontSize = 14.sp,
        color = colors.onSurface,
        fontWeight = FontWeight.SemiBold,
    )

    Column(modifier = modifier.fillMaxWidth()) {
        if (series.size >= 2) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                series.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(s.color)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            s.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .clip(RoundedCornerShape(14.dp))
                .pointerInput(dayIndex.size, domainMin, domainMax) {
                    val slop = viewConfiguration.touchSlop
                    val gutterPx = Y_GUTTER.toPx()
                    var lastUpUptime = 0L

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val plotW = (size.width - gutterPx).coerceAtLeast(1f)
                        var lastUptime = down.uptimeMillis

                        fun clampView() {
                            spanDays = spanDays.coerceIn(minSpan, fullSpan)
                            startDay = startDay.coerceIn(
                                domainMin,
                                max(domainMin, domainMax - spanDays),
                            )
                        }

                        // Second tap in quick succession resets the window.
                        if (lastUpUptime > 0L && down.uptimeMillis - lastUpUptime < 280L) {
                            spanDays = initialSpan
                            startDay = max(domainMin, domainMax - initialSpan)
                            inspectX = null
                            var stillDown = true
                            while (stillDown) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                stillDown = event.changes.any { it.pressed }
                            }
                            lastUpUptime = 0L
                            return@awaitEachGesture
                        }

                        // Undecided at first, and deliberately not consuming: a
                        // vertical drag must reach the page scroller underneath.
                        var mode = Mode.UNDECIDED
                        var reference = down.position

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            event.changes.firstOrNull()?.let { lastUptime = it.uptimeMillis }
                            if (pressed.isEmpty()) break

                            if (pressed.size >= 2) {
                                mode = Mode.TRANSFORM
                                inspectX = null
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)

                                if (zoom > 0f && zoom != 1f) {
                                    val focus = ((centroid.x - gutterPx) / plotW).coerceIn(0f, 1f)
                                    val focusDay = startDay + spanDays * focus
                                    val newSpan = (spanDays / zoom).coerceIn(minSpan, fullSpan)
                                    startDay = focusDay - newSpan * focus
                                    spanDays = newSpan
                                }
                                if (pan.x != 0f) startDay -= pan.x / plotW * spanDays
                                clampView()
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            val change = pressed.first()
                            val position = change.position

                            if (mode == Mode.UNDECIDED) {
                                val dx = position.x - down.position.x
                                val dy = position.y - down.position.y
                                val held = change.uptimeMillis - down.uptimeMillis
                                when {
                                    abs(dy) > slop && abs(dy) > abs(dx) * 1.2f -> {
                                        // Hand it to the scrolling page and stay out of the way.
                                        return@awaitEachGesture
                                    }
                                    abs(dx) > slop -> {
                                        mode = Mode.PAN
                                        reference = position
                                    }
                                    held > 200L -> {
                                        mode = Mode.INSPECT
                                        inspectX = position.x
                                    }
                                }
                                if (mode == Mode.UNDECIDED) continue
                            }

                            if (mode == Mode.PAN) {
                                startDay -= (position.x - reference.x) / plotW * spanDays
                                clampView()
                                reference = position
                            } else if (mode == Mode.INSPECT) {
                                inspectX = position.x
                            }
                            event.changes.forEach { it.consume() }
                        }
                        inspectX = null
                        lastUpUptime = lastUptime
                    }
                }
        ) {
            drawChart(
                series = series,
                dayIndex = dayIndex,
                startDay = startDay,
                spanDays = spanDays,
                inspectX = inspectX,
                zeroBased = zeroBased,
                guide = guide,
                valueLabel = valueLabel,
                unitSuffix = unitSuffix,
                measurer = measurer,
                axisLabelStyle = axisLabelStyle,
                tooltipTitleStyle = tooltipTitleStyle,
                tooltipValueStyle = tooltipValueStyle,
                surfaceColor = colors.surfaceContainerHigh,
                outlineColor = colors.outline,
                gridColor = palette.grid,
                axisColor = palette.axis,
                gutterPx = with(density) { Y_GUTTER.toPx() },
                axisHeightPx = with(density) { X_AXIS_HEIGHT.toPx() },
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Pinch to zoom  \u00b7  hold to read a value  \u00b7  double-tap to reset",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

private enum class Mode { UNDECIDED, INSPECT, PAN, TRANSFORM }

// ---------------------------------------------------------------- drawing

private fun DrawScope.drawChart(
    series: List<ChartSeries>,
    dayIndex: List<Long>,
    startDay: Float,
    spanDays: Float,
    inspectX: Float?,
    zeroBased: Boolean,
    guide: ChartGuide?,
    valueLabel: (Double) -> String,
    unitSuffix: String,
    measurer: TextMeasurer,
    axisLabelStyle: TextStyle,
    tooltipTitleStyle: TextStyle,
    tooltipValueStyle: TextStyle,
    surfaceColor: Color,
    outlineColor: Color,
    // Chart ink is passed in rather than read from a global: it is white-on-dark in one
    // theme and black-on-light in the other, and DrawScope is not a composable scope.
    gridColor: Color,
    axisColor: Color,
    gutterPx: Float,
    axisHeightPx: Float,
) {
    val plotLeft = gutterPx
    val plotTop = 10f
    val plotRight = size.width
    val plotBottom = size.height - axisHeightPx
    val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
    val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)

    fun xOf(day: Float) = plotLeft + (day - startDay) / spanDays * plotWidth

    // ---- vertical range from what is actually on screen ----
    val visibleLo = startDay - 1f
    val visibleHi = startDay + spanDays + 1f
    val visibleValues = series.flatMap { s ->
        s.points.filter { it.epochDay >= visibleLo && it.epochDay <= visibleHi }.map { it.value }
    }
    val pool = buildList {
        addAll(visibleValues.ifEmpty { series.flatMap { it.points.map { p -> p.value } } })
        guide?.let { add(it.value) }
        if (zeroBased) add(0.0)
    }
    var yLo = pool.minOrNull() ?: 0.0
    var yHi = pool.maxOrNull() ?: 1.0
    if (zeroBased) yLo = 0.0
    val headroom = (yHi - yLo).let { if (it < 1e-9) max(abs(yHi) * 0.1, 1.0) else it * 0.12 }
    yHi += headroom
    if (!zeroBased) yLo -= headroom

    val ticks = niceTicks(yLo, yHi, 5)
    val axisLo = ticks.values.firstOrNull() ?: yLo
    val axisHi = ticks.values.lastOrNull() ?: yHi
    val axisRange = (axisHi - axisLo).let { if (abs(it) < 1e-9) 1.0 else it }

    fun yOf(value: Double): Float =
        plotTop + plotHeight * (1.0 - (value - axisLo) / axisRange).toFloat()

    // ---- grid + y labels ----
    ticks.values.forEach { t ->
        val y = yOf(t)
        if (y < plotTop - 1 || y > plotBottom + 1) return@forEach
        drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1f)
        val layout: TextLayoutResult =
            measurer.measure(t.trimDecimals(ticks.decimals), axisLabelStyle)
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(plotLeft - 8f - layout.size.width, y - layout.size.height / 2f),
        )
    }

    // baseline
    drawLine(axisColor, Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), 1.2f)

    // ---- x labels ----
    val xTicks = dateTicks(startDay, spanDays)
    val maxLabels = (plotWidth / 62f).toInt().coerceAtLeast(2)
    val stride = (xTicks.size / maxLabels.toFloat()).let { if (it <= 1f) 1 else it.roundToInt() }
    xTicks.filterIndexed { index, _ -> index % stride == 0 }.forEach { (day, label) ->
        val x = xOf(day)
        if (x < plotLeft - 20 || x > plotRight + 20) return@forEach
        drawLine(
            gridColor.copy(alpha = 0.55f),
            Offset(x, plotTop),
            Offset(x, plotBottom),
            strokeWidth = 1f,
        )
        val layout = measurer.measure(label, axisLabelStyle)
        val lx = (x - layout.size.width / 2f).coerceIn(2f, size.width - layout.size.width - 2f)
        drawText(layout, topLeft = Offset(lx, plotBottom + 5f))
    }

    // ---- goal / reference line ----
    guide?.let { g ->
        val y = yOf(g.value)
        if (y in plotTop..plotBottom) {
            drawLine(
                color = g.color.copy(alpha = 0.7f),
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.4f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
            val layout = measurer.measure(
                g.label,
                axisLabelStyle.copy(color = g.color, fontSize = 10.sp),
            )
            drawText(
                layout,
                topLeft = Offset(plotRight - layout.size.width - 4f, y - layout.size.height - 3f),
            )
        }
    }

    // ---- bars ----
    val barSeries = series.filter { it.kind == SeriesKind.BAR }
    if (barSeries.isNotEmpty()) {
        val dayWidth = plotWidth / spanDays
        val groupWidth = (dayWidth * 0.68f).coerceAtLeast(1.5f)
        val slotWidth = (groupWidth / barSeries.size).coerceAtLeast(1.2f)
        val gap = if (slotWidth > 5f && barSeries.size > 1) 2f else 0f
        val barWidth = (slotWidth - gap).coerceAtLeast(1.2f)
        val radius = CornerRadius(min(4f, barWidth / 2f), min(4f, barWidth / 2f))
        val zeroY = yOf(max(0.0, axisLo))

        barSeries.forEachIndexed { seriesIndex, s ->
            s.points.forEach { p ->
                if (p.epochDay < visibleLo || p.epochDay > visibleHi) return@forEach
                if (p.value <= 0.0) return@forEach
                val centre = xOf(p.epochDay.toFloat())
                val groupLeft = centre - groupWidth / 2f
                val left = groupLeft + seriesIndex * slotWidth + gap / 2f
                val top = yOf(p.value).coerceIn(plotTop, plotBottom)
                if (left + barWidth < plotLeft || left > plotRight) return@forEach
                drawRoundRect(
                    color = s.color,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, (zeroY - top).coerceAtLeast(1.5f)),
                    cornerRadius = radius,
                )
            }
        }
    }

    // ---- lines ----
    val lineSeries = series.filter { it.kind == SeriesKind.LINE }
    val fillUnderLine = lineSeries.size == 1 && barSeries.isEmpty()

    lineSeries.forEach { s ->
        val visible = s.points
            .filter { it.epochDay >= visibleLo - spanDays && it.epochDay <= visibleHi + spanDays }
            .sortedBy { it.epochDay }
        if (visible.isEmpty()) return@forEach

        if (fillUnderLine && visible.size > 1) {
            val fill = Path().apply {
                moveTo(xOf(visible.first().epochDay.toFloat()), plotBottom)
                visible.forEach { lineTo(xOf(it.epochDay.toFloat()), yOf(it.value)) }
                lineTo(xOf(visible.last().epochDay.toFloat()), plotBottom)
                close()
            }
            clipRect(plotLeft, plotTop, plotRight, plotBottom) {
                drawPath(
                    path = fill,
                    brush = Brush.verticalGradient(
                        0f to s.color.copy(alpha = 0.30f),
                        1f to s.color.copy(alpha = 0.02f),
                        startY = plotTop,
                        endY = plotBottom,
                    ),
                )
            }
        }

        // Segments, dashed where days are missing so a gap never reads as a trend.
        clipRect(plotLeft, plotTop - 6f, plotRight, plotBottom + 6f) {
            for (i in 0 until visible.size - 1) {
                val a = visible[i]
                val b = visible[i + 1]
                val gapDays = b.epochDay - a.epochDay
                drawLine(
                    color = if (gapDays > 2) s.color.copy(alpha = 0.55f) else s.color,
                    start = Offset(xOf(a.epochDay.toFloat()), yOf(a.value)),
                    end = Offset(xOf(b.epochDay.toFloat()), yOf(b.value)),
                    strokeWidth = 2.4f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    pathEffect = if (gapDays > 2) {
                        PathEffect.dashPathEffect(floatArrayOf(7f, 7f))
                    } else null,
                )
            }

            val onScreen = visible.count { it.epochDay >= visibleLo && it.epochDay <= visibleHi }
            if (onScreen <= 70) {
                visible.forEach { p ->
                    val x = xOf(p.epochDay.toFloat())
                    if (x < plotLeft - 6 || x > plotRight + 6) return@forEach
                    val y = yOf(p.value)
                    // 2px surface ring keeps overlapping markers separable.
                    drawCircle(surfaceColor, radius = 5.2f, center = Offset(x, y))
                    drawCircle(s.color, radius = 3.4f, center = Offset(x, y))
                }
            }
        }
    }

    // ---- crosshair + value bubble ----
    if (inspectX != null && dayIndex.isNotEmpty()) {
        val touchedDay = startDay + ((inspectX - plotLeft) / plotWidth) * spanDays
        val nearest = dayIndex.minByOrNull { abs(it - touchedDay) } ?: return
        val x = xOf(nearest.toFloat())

        if (x >= plotLeft - 40f && x <= plotRight + 40f) {
            drawLine(
                color = outlineColor.copy(alpha = 0.9f),
                start = Offset(x, plotTop),
                end = Offset(x, plotBottom),
                strokeWidth = 1.2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 5f)),
            )

            val rows = series.mapNotNull { s ->
                s.points.firstOrNull { it.epochDay == nearest }?.let { s to it }
            }

            rows.forEach { (s, p) ->
                val y = yOf(p.value)
                drawCircle(surfaceColor, radius = 8.5f, center = Offset(x, y))
                drawCircle(s.color, radius = 6f, center = Offset(x, y))
            }

            // Bubble
            val title = nearest.formatFullDate()
            val titleLayout = measurer.measure(title, tooltipTitleStyle)
            val valueLayouts = rows.map { (s, p) ->
                val prefix = if (series.size > 1) "${s.name}: " else ""
                measurer.measure(
                    prefix + valueLabel(p.value) + unitSuffix,
                    tooltipValueStyle,
                )
            }

            val padding = 10f
            val lineGap = 3f
            val contentWidth = max(
                titleLayout.size.width.toFloat(),
                valueLayouts.maxOfOrNull { it.size.width.toFloat() } ?: 0f,
            )
            val contentHeight = titleLayout.size.height + lineGap +
                valueLayouts.sumOf { it.size.height } + lineGap * (valueLayouts.size - 1)
                    .coerceAtLeast(0)
            val bubbleWidth = contentWidth + padding * 2
            val bubbleHeight = contentHeight + padding * 2

            val anchorY = rows.minOfOrNull { yOf(it.second.value) } ?: plotTop
            var bubbleTop = anchorY - bubbleHeight - 14f
            if (bubbleTop < plotTop) bubbleTop = (anchorY + 16f).coerceAtMost(plotBottom - bubbleHeight)
            val bubbleLeft = (x - bubbleWidth / 2f).coerceIn(
                plotLeft + 2f,
                (plotRight - bubbleWidth - 2f).coerceAtLeast(plotLeft + 2f),
            )

            drawRoundRect(
                color = surfaceColor,
                topLeft = Offset(bubbleLeft, bubbleTop),
                size = Size(bubbleWidth, bubbleHeight),
                cornerRadius = CornerRadius(10f, 10f),
            )
            drawRoundRect(
                color = outlineColor,
                topLeft = Offset(bubbleLeft, bubbleTop),
                size = Size(bubbleWidth, bubbleHeight),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 1f),
            )

            drawText(titleLayout, topLeft = Offset(bubbleLeft + padding, bubbleTop + padding))
            var cursorY = bubbleTop + padding + titleLayout.size.height + lineGap
            valueLayouts.forEachIndexed { index, layout ->
                drawText(layout, topLeft = Offset(bubbleLeft + padding, cursorY))
                cursorY += layout.size.height + lineGap
                // Colour chip beside each value so identity is never colour-alone text.
                if (series.size > 1) {
                    val chipY = cursorY - layout.size.height - lineGap + layout.size.height / 2f
                    drawCircle(
                        color = rows[index].first.color,
                        radius = 3.2f,
                        center = Offset(bubbleLeft + padding - 5f, chipY),
                    )
                }
            }
        }
    }
}
