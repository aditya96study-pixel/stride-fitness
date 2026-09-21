package com.aditya.stride

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aditya.stride.data.ProfileStore
import com.aditya.stride.data.ThemeCache
import com.aditya.stride.ui.nav.StrideRoot
import com.aditya.stride.ui.theme.StrideTheme
import com.aditya.stride.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DESTINATION = "destination"
    }

    private var pendingDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDestination = intent?.getStringExtra(EXTRA_DESTINATION)

        // Read synchronously, before the first frame. The theme is stored in DataStore,
        // which is asynchronous, so a cached copy is what stops the app flashing the
        // system theme for a frame on every cold start.
        val cachedMode = ThemeCache.read(this)

        setContent {
            val store = remember { ProfileStore(applicationContext) }
            val mode by produceState(cachedMode, store) {
                store.profile.collect { value = it.themeMode }
            }
            StrideTheme(darkTheme = mode.isDarkTheme()) {
                StrideRoot(
                    requestedDestination = pendingDestination,
                    onDestinationConsumed = { pendingDestination = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDestination = intent.getStringExtra(EXTRA_DESTINATION)
    }
}
