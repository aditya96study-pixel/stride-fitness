package com.aditya.stride.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aditya.stride.ui.screens.DashboardScreen
import com.aditya.stride.ui.screens.ExerciseScreen
import com.aditya.stride.ui.screens.FoodScreen
import com.aditya.stride.ui.screens.LogHubScreen
import com.aditya.stride.ui.screens.ProfileScreen
import com.aditya.stride.ui.screens.RemindersScreen
import com.aditya.stride.ui.screens.RunDetailScreen
import com.aditya.stride.ui.screens.RunScreen
import com.aditya.stride.ui.screens.RunsListScreen
import com.aditya.stride.ui.screens.TrendsScreen
import com.aditya.stride.ui.screens.WaterScreen
import com.aditya.stride.ui.screens.WeightScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val RUN = "run"
    const val LOG = "log"
    const val TRENDS = "trends"
    const val PROFILE = "profile"

    const val WEIGHT = "weight"
    const val FOOD = "food"
    const val WATER = "water"
    const val EXERCISE = "exercise"
    const val REMINDERS = "reminders"
    const val RUNS_LIST = "runs"
    const val RUN_DETAIL = "run_detail"
}

private data class BarItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val barItems = listOf(
    BarItem(Routes.DASHBOARD, "Today", Icons.Rounded.Today),
    BarItem(Routes.RUN, "Run", Icons.Rounded.DirectionsRun),
    BarItem(Routes.LOG, "Log", Icons.Rounded.AddCircleOutline),
    BarItem(Routes.TRENDS, "Trends", Icons.Rounded.Insights),
    BarItem(Routes.PROFILE, "You", Icons.Rounded.Person),
)

@Composable
fun StrideRoot(
    requestedDestination: String?,
    onDestinationConsumed: () -> Unit,
) {
    val navController = rememberNavController()

    // A reminder notification can ask for a specific logging screen.
    LaunchedEffect(requestedDestination) {
        val target = requestedDestination ?: return@LaunchedEffect
        val route = when (target) {
            "food" -> Routes.FOOD
            "water" -> Routes.WATER
            "weight" -> Routes.WEIGHT
            "exercise" -> Routes.EXERCISE
            "run" -> Routes.RUN
            else -> Routes.DASHBOARD
        }
        navController.navigate(route) { launchSingleTop = true }
        onDestinationConsumed()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute in barItems.map { it.route }) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    barItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { navController.switchTab(item.route) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpen = { route -> navController.navigate(route) },
                )
            }
            composable(Routes.RUN) {
                RunScreen(
                    onOpenRuns = { navController.navigate(Routes.RUNS_LIST) },
                    onOpenRun = { id -> navController.navigate("${Routes.RUN_DETAIL}/$id") },
                )
            }
            composable(Routes.LOG) {
                LogHubScreen(onOpen = { route -> navController.navigate(route) })
            }
            composable(Routes.TRENDS) { TrendsScreen() }
            composable(Routes.PROFILE) {
                ProfileScreen(onOpenReminders = { navController.navigate(Routes.REMINDERS) })
            }

            composable(Routes.WEIGHT) { WeightScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.FOOD) { FoodScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.WATER) { WaterScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.EXERCISE) { ExerciseScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.REMINDERS) {
                RemindersScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.RUNS_LIST) {
                RunsListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRun = { id -> navController.navigate("${Routes.RUN_DETAIL}/$id") },
                )
            }
            composable("${Routes.RUN_DETAIL}/{runId}") { entry ->
                val id = entry.arguments?.getString("runId")?.toLongOrNull() ?: 0L
                RunDetailScreen(runId = id, onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
