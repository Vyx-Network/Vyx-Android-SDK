package com.vyx.sdk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.*

/**
 * Background service that maintains QUIC connection to Vyx server using Go Mobile
 *
 * This service runs in the foreground with a notification to ensure
 * it stays alive and maintains the connection for proxy operations.
 */
class VyxService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var quicClient: QuicClient? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var config: VyxConfig? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var activeNetwork: Network? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vyx_node_service"
        const val EXTRA_CONFIG = "config"

        @Volatile
        private var isRunning = false

        fun isServiceRunning(): Boolean = isRunning
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("Vyx", "VyxService onCreate")
        isRunning = true

        // Acquire wake lock to keep CPU running for network operations
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VyxNode::ServiceWakeLock"
        ).apply {
            acquire(10*60*1000L /*10 minutes*/)
        }

        // Register network change listener
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("Vyx", "VyxService onStartCommand")

        // Prevent duplicate connection attempts
        if (quicClient != null && quicClient?.isConnected() == true) {
            Log.w("Vyx", "QUIC client is already connected, ignoring duplicate start command")
            return START_STICKY
        }

        // Extract configuration from intent
        config = intent?.getSerializableExtra(EXTRA_CONFIG) as? VyxConfig

        if (config == null) {
            Log.e("Vyx", "No configuration provided, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        // Start as foreground service with notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Start QUIC connection using Go Mobile (only if not already connected)
        if (quicClient == null) {
            startQuicConnection()
        }

        return START_STICKY // Restart service if killed by system
    }

    override fun onDestroy() {
        Log.d("Vyx", "VyxService onDestroy")
        isRunning = false

        // Unregister network callback
        unregisterNetworkCallback()

        // Clean up resources
        serviceScope.cancel()
        quicClient?.disconnect()

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // We don't provide binding
        return null
    }

    private fun startQuicConnection() {
        val cfg = config ?: return

        Log.i("Vyx", "Starting QUIC connection with automatic server discovery")

        quicClient = QuicClient(
            config = cfg,
            context = applicationContext
        )

        // Start connection (runs in Go goroutines with automatic server discovery)
        quicClient?.connect()
    }

    private fun createNotification(): Notification {
        val channelName = config?.notificationChannelName ?: "Vyx Node Service"
        val title = config?.notificationTitle ?: "Vyx Node Running"
        val message = config?.notificationMessage ?: "Earning rewards by sharing bandwidth"
        val icon = config?.notificationIcon ?: android.R.drawable.ic_dialog_info

        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_LOW // Low priority, no sound
            ).apply {
                description = "Vyx Node background service"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        // Build notification
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(icon)
            .setOngoing(true) // Cannot be dismissed
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun registerNetworkCallback() {
        try {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)

                    val previousNetwork = activeNetwork
                    activeNetwork = network

                    if (previousNetwork != null && previousNetwork != network) {
                        Log.i("Vyx", "Network switched: $previousNetwork -> $network - triggering reconnection")
                        // Network changed (e.g., WiFi -> Mobile) - reconnect immediately
                        reconnectQuicClient()
                    } else if (previousNetwork == null) {
                        Log.i("Vyx", "Network restored: $network - triggering reconnection")
                        // Network came back after being lost - reconnect immediately
                        reconnectQuicClient()
                    } else {
                        Log.d("Vyx", "Network available: $network")
                    }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    Log.w("Vyx", "Network lost: $network")

                    // Clear active network if it was lost
                    if (activeNetwork == network) {
                        activeNetwork = null
                    }
                    // Connection will be detected as lost by Go client
                    // Will reconnect when network returns via onAvailable()
                }
            }

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
            Log.d("Vyx", "Network callback registered")

        } catch (e: Exception) {
            Log.e("Vyx", "Failed to register network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let {
                val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(it)
                Log.d("Vyx", "Network callback unregistered")
            }
        } catch (e: Exception) {
            Log.e("Vyx", "Failed to unregister network callback", e)
        }
    }

    private fun reconnectQuicClient() {
        serviceScope.launch {
            try {
                Log.i("Vyx", "Network change detected - reconnecting immediately...")

                // Disconnect old client
                quicClient?.disconnect()
                quicClient = null

                // Small delay to ensure cleanup
                delay(500)

                // Start new connection
                startQuicConnection()

                Log.i("Vyx", "Reconnection initiated")
            } catch (e: Exception) {
                Log.e("Vyx", "Failed to reconnect QUIC client", e)
            }
        }
    }
}
