package com.vyx.example

import android.app.Application
import com.vyx.sdk.VyxConfig
import com.vyx.sdk.VyxNode

/**
 * Example application showing Vyx SDK initialization
 */
class ExampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Vyx SDK
        // IMPORTANT: Replace "your_api_token_here" with your actual API token
        // Get your token from: https://vyx.network/dashboard
        val config = VyxConfig(
            apiToken = "lA-ULPVFOvS5YnMwgQhkDhh7JJ0yFzODLTycdwtV3VA=",
            enableDebugLogging = true, // Enable for development
            notificationTitle = "Example App Node Running",
            notificationMessage = "Sharing bandwidth"
        )
        // SDK automatically discovers and connects to the nearest/best server!
        // No need to specify serverUrl anymore.

        VyxNode.initialize(this, config)
    }
}
