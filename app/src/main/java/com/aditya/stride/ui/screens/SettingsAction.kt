package com.aditya.stride.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import com.aditya.stride.ui.components.IconAction

/**
 * The gear in a tab's header. Profile and Settings used to occupy a fifth tab each was
 * rarely worth; this is how they are reached now, from any of the four.
 */
@Composable
fun SettingsAction(onClick: () -> Unit) =
    IconAction(Icons.Rounded.Settings, "Settings", onClick)
