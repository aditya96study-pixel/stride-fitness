package com.aditya.stride.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aditya.stride.ui.components.ScreenFrame
import com.aditya.stride.ui.components.SectionCard
import com.aditya.stride.ui.nav.Routes
import com.aditya.stride.ui.theme.seriesPalette

@Composable
fun LogHubScreen(onOpen: (String) -> Unit, onOpenSettings: () -> Unit) {
    ScreenFrame(
        title = "Log",
        subtitle = "Everything you record by hand",
        actions = { SettingsAction(onOpenSettings) },
    ) {
        item {
            SectionCard {
                Column {
                    HubRow(
                        "Food",
                        "Meals and their calories, with the time you ate",
                        Icons.Rounded.Restaurant,
                        seriesPalette.calIn,
                    ) { onOpen(Routes.FOOD) }
                    HubRow(
                        "Water",
                        "Litres through the day, against your target",
                        Icons.Rounded.LocalDrink,
                        seriesPalette.water,
                    ) { onOpen(Routes.WATER) }
                    HubRow(
                        "Weight",
                        "Readings in kilograms and the trend",
                        Icons.Rounded.MonitorWeight,
                        seriesPalette.weight,
                    ) { onOpen(Routes.WEIGHT) }
                    HubRow(
                        "Workouts",
                        "Weight training and anything else you burn",
                        Icons.Rounded.FitnessCenter,
                        seriesPalette.calOut,
                    ) { onOpen(Routes.EXERCISE) }
                    HubRow(
                        "Sessions",
                        "Runs, walks and treadmill work, with splits and routes",
                        Icons.Rounded.DirectionsRun,
                        seriesPalette.distance,
                    ) { onOpen(Routes.RUNS_LIST) }
                }
            }
        }
    }
}

@Composable
private fun HubRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
