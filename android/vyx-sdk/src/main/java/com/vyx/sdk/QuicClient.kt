package com.vyx.sdk

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import vyxclient.Callback
import vyxclient.Client
import vyxclient.Vyxclient
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.IOException
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * QUIC client that maintains connection to Vyx server using Go Mobile
 * Handles authentication, message routing, and proxy connections
 *
 * Automatically discovers and connects to the nearest/best server
 */
class QuicClient(
    private val config: VyxConfig,
    private val context: Context
) : Callback {

    private var ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val clientConnections = ConcurrentHashMap<String, ProxyConnection>()

    private var goClient: Client? = null
    private var connectionJob: Job? = null // Track active connection job to prevent duplicates

    @Volatile
    private var isConnecting = false // Track if connection attempt is in progress

    @Volatile
    private var isConnected = false

    @Volatile
    private var currentServerUrl: String = VyxConfig.FALLBACK_SERVER

    /**
     * Start connection loop with automatic server discovery and reconnection
     *
     * Features:
     * - Discovers optimal server based on latency and load
     * - Go client handles automatic reconnection with exponential backoff
     * - After 3 failures, rotates to fallback servers (us.vyx.network, eu.vyx.network, proxy.vyx.network)
     * - Network changes trigger immediate reconnection via VyxService
     */
    fun connect() {
        // CRITICAL: Prevent duplicate connection attempts
        if (isConnecting) {
            Log.w("Vyx", "Connection already in progress, ignoring duplicate connect() call")
            return
        }

        // CRITICAL: Cancel any existing connection attempt to prevent duplicates
        connectionJob?.cancel()

        // Recreate coroutine scope if it was cancelled
        if (!ioScope.isActive) {
            Log.d("Vyx", "Recreating coroutine scope")
            ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }

        // Mark as connecting BEFORE starting async work
        isConnecting = true

        // Discover optimal server in background
        connectionJob = ioScope.launch {
            try {
                Log.i("Vyx", "Discovering optimal server...")
                val optimalServer = ServerDiscovery.getOptimalServer(
                    VyxConfig.API_BASE_URL,
                    VyxConfig.FALLBACK_SERVER
                )

                currentServerUrl = optimalServer
                Log.i("Vyx", "Selected server: $currentServerUrl")

                // Start QUIC connection to discovered server
                // Go client will automatically add fallback servers for rotation
                startConnection()
            } catch (e: CancellationException) {
                Log.d("Vyx", "Connection attempt cancelled (newer attempt started)")
            } catch (e: Exception) {
                Log.e("Vyx", "Server discovery failed, using fallback", e)
                currentServerUrl = VyxConfig.FALLBACK_SERVER
                startConnection()
            } finally {
                // Clear connecting flag when done
                isConnecting = false

                // Clear job reference when done
                if (connectionJob?.isActive == false) {
                    connectionJob = null
                }
            }
        }
    }

    /**
     * Start QUIC connection to current server
     */
    private fun startConnection() {
        Log.d("Vyx", "Starting QUIC connection to $currentServerUrl")

        // Create metadata JSON
        val metadata = JSONObject().apply {
            put("client_type", "android_sdk")
            put("os", "android")
            put("os_version", Build.VERSION.RELEASE)
            put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("sdk_version", BuildConfig.SDK_VERSION)
            put("app_package", context.packageName)
        }

        // Create Go Mobile client with this as callback
        goClient = Vyxclient.newClient(
            currentServerUrl,
            config.apiToken,
            "android_sdk",
            metadata.toString(),
            this
        )

        // Start connection loop (runs in Go goroutines)
        goClient?.start()
    }

    /**
     * Disconnect and stop reconnection attempts
     */
    fun disconnect() {
        Log.i("Vyx", "Disconnecting QUIC client")

        // Clear connecting flag to allow future connections
        isConnecting = false

        // Cancel any pending connection jobs FIRST
        connectionJob?.cancel()
        connectionJob = null

        // Stop Go client (this stops the connection loop)
        goClient?.let {
            try {
                it.stop()
                Log.d("Vyx", "Go client stopped")
            } catch (e: Exception) {
                Log.e("Vyx", "Error stopping Go client", e)
            }
        }
        goClient = null

        // Close all proxy connections
        clientConnections.values.forEach { it.close() }
        clientConnections.clear()

        // Cancel coroutines
        ioScope.cancel()

        isConnected = false
    }

    /**
     * Check if currently connected to server
     */
    fun isConnected(): Boolean = isConnected && goClient != null

    /**
     * Check if connection attempt is currently in progress
     */
    fun isConnecting(): Boolean = isConnecting

    // ========== MessageCallback Implementation ==========

    override fun onConnected() {
        Log.i("Vyx", "Successfully connected and authenticated")
        isConnected = true
    }

    override fun onDisconnected(reason: String?) {
        Log.w("Vyx", "Disconnected: ${reason ?: "unknown reason"}")
        isConnected = false

        // Close all connections on disconnect
        clientConnections.values.forEach { it.close() }
        clientConnections.clear()

        // The Go client will automatically retry with exponential backoff (1s -> 2s -> 4s -> max 2min)
        // After 3 failures, it will rotate to fallback servers
        // VyxService will also detect network changes and trigger immediate reconnection
        Log.i("Vyx", "Go client will automatically attempt reconnection with exponential backoff...")
    }

    override fun onMessage(messageType: String?, id: String?, addr: String?, data: String?) {
        val type = messageType ?: return
        val connId = id ?: ""

        // Log.d("Vyx", "Received message: $type (id=$connId)")

        when (type) {
            "auth_success" -> {
                Log.i("Vyx", "Authenticated succesfully")
            }

            "connect" -> {
                // Server wants us to open TCP connection
                if (addr != null) {
                    // Log.d("Vyx", "Proxy connect request to $addr")
                    ioScope.launch {
                        handleProxyConnect(connId, addr, data)
                    }
                }
            }

            "data" -> {
                // Forward data to existing TCP connection
                val connection = clientConnections[connId]
                if (connection != null && data != null) {
                    try {
                        val bytes = Base64.decode(data, Base64.DEFAULT)
                        connection.sendData(bytes)
                    } catch (e: Exception) {
                        Log.e("Vyx", "Error forwarding data to connection $connId", e)
                    }
                } else {
                    Log.w("Vyx", "Received data for unknown connection: $connId")
                }
            }

            "close" -> {
                // Close TCP connection
                val connection = clientConnections.remove(connId)
                connection?.close()
                Log.d("Vyx", "Closed connection: $connId")
            }

            "ping" -> {
                // Go client automatically responds with pong
                Log.v("Vyx", "Ping received")
            }

            "error" -> {
                Log.e("Vyx", "Server error: $data")
            }

            else -> {
                Log.w("Vyx", "Unknown message type: $type")
            }
        }
    }

    override fun onLog(message: String?) {
        Log.v("Vyx", "[Go] ${message ?: ""}")
    }

    // ========== Proxy Connection Handling ==========

    /**
     * Handle proxy connection request from server
     */
    private suspend fun handleProxyConnect(id: String, targetAddr: String, initialData: String?) {
        try {
            val connection = ProxyConnection(
                targetAddr = targetAddr,
                onDataReceived = { data ->
                    // Send data back to server via Go client
                    val encoded = Base64.encodeToString(data, Base64.NO_WRAP)
                    goClient?.sendMessage("data", id, "", encoded)
                },
                onClosed = {
                    // Notify server of closure
                    clientConnections.remove(id)
                    goClient?.sendMessage("close", id, "", "")
                }
            )

            if (connection.connect()) {
                clientConnections[id] = connection

                // Send confirmation to server
                goClient?.sendMessage("connected", id, "", "")

                // Send initial data if present
                if (!initialData.isNullOrEmpty()) {
                    val bytes = Base64.decode(initialData, Base64.DEFAULT)
                    connection.sendData(bytes)
                }

                Log.d("Vyx", "Proxy connection established: $id -> $targetAddr")
            } else {
                Log.e("Vyx", "Failed to establish proxy connection to $targetAddr")
                goClient?.sendMessage("close", id, "", "")
            }

        } catch (e: Exception) {
            Log.e("Vyx", "Error handling proxy connect", e)
            goClient?.sendMessage("close", id, "", "")
        }
    }

    /**
     * Proxy connection to target server
     */
    private class ProxyConnection(
        val targetAddr: String,
        val onDataReceived: (ByteArray) -> Unit,
        val onClosed: () -> Unit
    ) {
        private var socket: Socket? = null
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        @Volatile
        private var isRunning = false

        suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
            try {
                val (host, port) = parseAddress(targetAddr)

                socket = Socket(host, port).apply {
                    tcpNoDelay = true
                    keepAlive = true
                    soTimeout = 0 // No read timeout
                }

                isRunning = true

                // Start reading from socket
                scope.launch {
                    readFromSocket()
                }

                true
            } catch (e: Exception) {
                Log.e("Vyx", "Failed to connect to $targetAddr", e)
                false
            }
        }

        private suspend fun readFromSocket() = withContext(Dispatchers.IO) {
            try {
                val inputStream = socket?.getInputStream()
                val buffer = ByteArray(32768) // 32KB buffer

                while (isRunning && socket?.isConnected == true) {
                    try {
                        val bytesRead = inputStream?.read(buffer) ?: -1
                        if (bytesRead == -1) {
                            // EOF reached
                            break
                        }

                        if (bytesRead > 0) {
                            val data = buffer.copyOf(bytesRead)
                            onDataReceived(data)
                        }
                    } catch (e: IOException) {
                        if (isRunning) {
                            Log.e("Vyx", "Error reading from socket", e)
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e("Vyx", "Error in socket read loop", e)
            } finally {
                close()
            }
        }

        fun sendData(data: ByteArray) {
            scope.launch(Dispatchers.IO) {
                try {
                    socket?.getOutputStream()?.write(data)
                    socket?.getOutputStream()?.flush()
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e("Vyx", "Error writing to socket", e)
                    }
                    close()
                }
            }
        }

        fun close() {
            if (!isRunning) return
            isRunning = false

            try {
                socket?.close()
                scope.cancel()
                onClosed()
            } catch (e: Exception) {
                Log.e("Vyx", "Error closing connection", e)
            }
        }

        private fun parseAddress(addr: String): Pair<String, Int> {
            val parts = addr.split(":")
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 80
            return Pair(host, port)
        }
    }
}
