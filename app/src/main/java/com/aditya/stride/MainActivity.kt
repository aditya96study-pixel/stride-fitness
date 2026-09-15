package com.aditya.stride

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.aditya.stride.ui.nav.StrideRoot
import com.aditya.stride.ui.theme.StrideTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DESTINATION = "destination"
    }

    private var pendingDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDestination = intent?.getStringExtra(EXTRA_DESTINATION)

        setContent {
            StrideTheme {
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
