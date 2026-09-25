package com.aditya.stride.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aditya.stride.data.BalancePeriod
import com.aditya.stride.data.EnergyBalance
import com.aditya.stride.data.PeriodBalance
import com.aditya.stride.ui.theme.StatusCritical
import com.aditya.stride.ui.theme.StatusGood
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val dayMonthFmt = DateTimeFormatter.ofPattern("d MMM")
private val monthFmt = DateTimeFormatter.ofPattern("MMMM")
private val monthYearFmt = DateTimeFormatter.ofPattern("MMM yyyy")

/** "+1,240" or "−860", with a real minus sign, matching the Today screen. */
fun Double.signedKcal(): String {
    val rounded = roundToInt()
    return (if (rounded >= 0) "+" else "−") + String.format("%,d", abs(rounded))
}

/** Surplus in the same red the Today screen uses for "above maintenance", deficit green. */
fun netColour(netKcal: Double): Color = if (netKcal >= 0) StatusCritical else StatusGood

fun PeriodBalance.periodLabel(period: BalancePeriod, isCurrent: Boolean): String {
    val first = LocalDate.ofEpochDay(start)
    val last = LocalDate.ofEpochDay(end)
    return when (period) {
        BalancePeriod.WEEK -> when {
            isCurrent -> "This week"
            first.month == last.month -> "${first.dayOfMonth}–${last.format(dayMonthFmt)}"
            else -> "${first.format(dayMonthFmt)} – ${last.format(dayMonthFmt)}"
        }
        BalancePeriod.MONTH -> when {
            isCurrent -> "This month"
            first.year == LocalDate.now().year -> first.format(monthFmt)
            else -> first.format(monthYearFmt)
        }
    }
}

fun PeriodBalance.daysCaption(): String =
    if (daysLogged == 1) "1 day logged" else "$daysLogged days logged"

/**
 * Net calories per week or month: whether you have been eating more or less than you
 * burn. The current period sits on top; earlier ones below, each with a bar growing
 * right for a surplus and left for a deficit, on one shared scale.
 */
@Composable
fun NetBalanceCard(balances: Map<BalancePeriod, List<PeriodBalance>>) {
    var period by rememberSaveable { mutableStateOf(BalancePeriod.WEEK) }
    val rows = balances[period].orEmpty()

    SectionCard(
        title = "Net calories",
        subtitle = "Eaten minus burned, per ${if (period == BalancePeriod.WEEK) "week" else "month"}",
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BalancePeriod.entries.forEach { option ->
                    Chip(
                        label = option.label,
                        onClick = { period = option },
                        selected = period == option,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            val current = rows.firstOrNull()
            if (current == null || rows.all { it.daysLogged == 0 }) {
                Hint("Log meals to see whether you are eating more or less than you burn.")
            } else {
                BalanceDetail(current, rows, period)
            }
        }
    }
}

@Composable
private fun BalanceDetail(current: PeriodBalance, rows: List<PeriodBalance>, period: BalancePeriod) {
    Column {
        if (current.daysLogged > 0) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    current.netKcal.signedKcal(),
                    style = MaterialTheme.typography.displaySmall,
                    color = netColour(current.netKcal),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "kcal",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(
                (if (current.netKcal >= 0) "surplus " else "deficit ") +
                    (if (period == BalancePeriod.WEEK) "this week" else "this month") +
                    " so far  ·  ${current.daysCaption()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${current.averageNetKcal.signedKcal()} kcal a day on average  ·  " +
                    "about ${kgEquivalent(current.netKcal)} kg",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        } else {
            Hint(
                "Nothing counted " +
                    (if (period == BalancePeriod.WEEK) "this week" else "this month") +
                    " yet — a day is added once it is over."
            )
        }

        Spacer(Modifier.height(16.dp))
        val scale = rows.maxOf { abs(it.netKcal) }.coerceAtLeast(1.0)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEachIndexed { index, row ->
                BalanceRow(row, row.periodLabel(period, isCurrent = index == 0), scale)
            }
        }

        Spacer(Modifier.height(12.dp))
        Hint(
            "Burned is resting metabolism × 1.2 for ordinary daily living, plus the " +
                "exercise you logged — the same sum as the Today screen. Only days with " +
                "food logged count, and today is added once it is over, so a half-logged " +
                "day never reads as a deficit."
        )
    }
}

private fun kgEquivalent(netKcal: Double): String {
    val kg = netKcal / EnergyBalance.KCAL_PER_KG
    return (if (kg >= 0) "+" else "−") + String.format("%.2f", abs(kg))
}

@Composable
private fun BalanceRow(row: PeriodBalance, label: String, scale: Double) {
    val logged = row.daysLogged > 0
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(0.34f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (logged) row.daysCaption() else "nothing logged",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        DivergingBar(
            fraction = if (logged) (row.netKcal / scale).toFloat() else 0f,
            colour = netColour(row.netKcal),
            modifier = Modifier.weight(0.4f),
        )
        Text(
            if (logged) row.netKcal.signedKcal() else "—",
            style = MaterialTheme.typography.bodyMedium,
            color = if (logged) netColour(row.netKcal) else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.26f),
        )
    }
}

/** A bar from a centre line: right for positive [fraction], left for negative. */
@Composable
private fun DivergingBar(fraction: Float, colour: Color, modifier: Modifier = Modifier) {
    val width = abs(fraction).coerceIn(0f, 1f)
    Row(
        modifier
            .height(14.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
            if (fraction < 0f && width > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(width)
                        .height(10.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                        .background(colour)
                )
            }
        }
        Box(
            Modifier
                .width(1.dp)
                .height(14.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            if (fraction > 0f && width > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(width)
                        .height(10.dp)
                        .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                        .background(colour)
                )
            }
        }
    }
}
