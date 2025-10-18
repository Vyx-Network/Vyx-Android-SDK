package com.vyx.sdk

import java.io.Serializable

/**
 * Configuration for Vyx SDK
 */
data class VyxConfig(
    /**
     * API token from Vyx dashboard
     * Get this from: https://vyx.network/dashboard -> API Keys
     */
    val apiToken: String,

    /**
     * Enable debug logging
     */
    val enableDebugLogging: Boolean = false,

    /**
     * Notification channel name (shown to users)
     */
    val notificationChannelName: String = "Vyx Node Service",

    /**
     * Notification title (shown in status bar)
     */
    val notificationTitle: String = "Vyx Node Running",

    /**
     * Notification message
     */
    val notificationMessage: String = "Earning rewards by sharing bandwidth",

    /**
     * Notification icon resource ID
     * Defaults to android.R.drawable.ic_dialog_info
     * Set to your app's icon for better UX
     */
    val notificationIcon: Int = android.R.drawable.ic_dialog_info
) : Serializable {

    // Internal configuration (not configurable by developers)
    internal companion object {
        /**
         * API base URL for server discovery
         * SDK automatically discovers and connects to the nearest/best server
         */
        const val API_BASE_URL = "https://vyx.network"

        /**
         * Fallback server if discovery fails
         */
        const val FALLBACK_SERVER = "us.vyx.network:8443"
    }

    init {
        require(apiToken.isNotBlank()) { "API token cannot be blank" }
    }
}
