package com.raven.application

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raven.application.bluetooth.BluetoothViewModel
import com.raven.application.ui.PermissionWrapper
import com.raven.application.ui.RavenApp
import com.raven.application.ui.theme.RavenTheme
import org.osmdroid.config.Configuration

/**
 * Raven Android entry point.
 */
class MainActivity : ComponentActivity() {
    private var currentIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentIntent = intent
        
        // Initialize osmdroid configuration
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        setContent {
            RavenTheme(dynamicColor = false) {
                PermissionWrapper {
                    val meshViewModel: BluetoothViewModel = viewModel()
                    LaunchedEffect(currentIntent) {
                        meshViewModel.startService()
                        handleIntent(currentIntent, meshViewModel)
                    }
                    RavenApp(meshViewModel = meshViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntent = intent
    }

    private fun handleIntent(intent: Intent?, viewModel: BluetoothViewModel) {
        if (intent?.getStringExtra("EXTRA_ACTION") == "NAVIGATE_TO_SOS") {
            val sender = intent.getStringExtra("EXTRA_SENDER")
            val peer = viewModel.peerTelemetry.value[sender]
            if (peer != null) {
                viewModel.navigateTo(peer)
            }
        }
    }
}
