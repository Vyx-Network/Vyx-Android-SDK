package com.vyx.sdk

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.min

/**
 * Server information from API
 */
data class ServerInfo(
    val id: String,
    val name: String,
    val region: String,
    val address: String,
    val status: String,
    val connections: ConnectionInfo
) {
    data class ConnectionInfo(
        val current: Long,
        val maximum: Long,
        val available: Long,
        val utilizationPercent: Double
    )
}

/**
 * Server discovery and selection for optimal connectivity
 */
object ServerDiscovery {

    private const val TAG = "VyxServerDiscovery"
    private const val DISCOVERY_TIMEOUT_MS = 5000
    private const val LATENCY_TEST_TIMEOUT_MS = 3000

    /**
     * Discover available servers from API
     */
    suspend fun discoverServers(apiUrl: String): List<ServerInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$apiUrl/api/servers")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = DISCOVERY_TIMEOUT_MS
            connection.readTimeout = DISCOVERY_TIMEOUT_MS

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("API returned ${connection.responseCode}")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val serversArray = json.getJSONArray("servers")

            val servers = mutableListOf<ServerInfo>()
            for (i in 0 until serversArray.length()) {
                val server = serversArray.getJSONObject(i)
                val connections = server.getJSONObject("connections")

                servers.add(
                    ServerInfo(
                        id = server.getString("id"),
                        name = server.getString("name"),
                        region = server.getString("region"),
                        address = server.getString("address"),
                        status = server.getString("status"),
                        connections = ServerInfo.ConnectionInfo(
                            current = connections.getLong("current"),
                            maximum = connections.getLong("maximum"),
                            available = connections.getLong("available"),
                            utilizationPercent = connections.getDouble("utilization_percent")
                        )
                    )
                )
            }

            Log.d(TAG, "Discovered ${servers.size} servers from API")
            servers
        } catch (e: Exception) {
            Log.e(TAG, "Failed to discover servers: ${e.message}", e)
            throw e
        }
    }

    /**
     * Test latency to a server by establishing TCP connection
     */
    suspend fun testLatency(address: String): Long = withContext(Dispatchers.IO) {
        try {
            // Parse address (e.g., "us.vyx.network:8443")
            val parts = address.split(":")
            val host = parts[0]
            // Test on HTTPS port (443) instead of QUIC port (UDP)
            val port = 443

            val startTime = System.currentTimeMillis()

            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), LATENCY_TEST_TIMEOUT_MS)
            }

            val latency = System.currentTimeMillis() - startTime
            Log.d(TAG, "Latency to $host: ${latency}ms")
            latency
        } catch (e: Exception) {
            // Connection failed, return high latency
            Log.w(TAG, "Failed to test latency to $address: ${e.message}")
            5000L // 5 seconds as penalty
        }
    }

    /**
     * Score and rank servers based on load and latency
     */
    data class ServerScore(
        val server: ServerInfo,
        val latency: Long,
        val score: Double
    )

    /**
     * Select the best server based on load and latency
     * Algorithm:
     * - Filter healthy servers
     * - Skip overloaded servers (>90% utilization)
     * - Test latency to remaining servers
     * - Score = (load * 0.6) + (latency/10 * 0.4)
     * - Select lowest score
     */
    suspend fun selectBestServer(servers: List<ServerInfo>): ServerInfo? = withContext(Dispatchers.IO) {
        if (servers.isEmpty()) {
            Log.w(TAG, "No servers available")
            return@withContext null
        }

        // Filter healthy servers
        val healthy = servers.filter { it.status == "healthy" }
        val candidates = healthy.ifEmpty {
            Log.w(TAG, "No healthy servers, using all servers")
            servers
        }

        if (candidates.size == 1) {
            Log.i(TAG, "Selected server: ${candidates[0].name} (${candidates[0].address}) - only available")
            return@withContext candidates[0]
        }

        // Test latency to all servers in parallel and score them
        val scores = candidates
            .filter { it.connections.utilizationPercent <= 90.0 } // Skip overloaded
            .map { server ->
                async {
                    val latency = testLatency(server.address)

                    // Calculate score: weighted combination of load and latency
                    // Load weight: 60%, Latency weight: 40%
                    val loadScore = server.connections.utilizationPercent
                    val latencyScore = latency.toDouble() / 10.0 // Normalize to 0-100 range

                    val totalScore = (loadScore * 0.6) + (latencyScore * 0.4)

                    Log.d(
                        TAG,
                        "Server ${server.name}: load=${"%.1f".format(loadScore)}%, " +
                                "latency=${latency}ms, score=${"%.1f".format(totalScore)}"
                    )

                    ServerScore(server, latency, totalScore)
                }
            }
            .awaitAll()

        if (scores.isEmpty()) {
            // All servers overloaded, pick least loaded
            val best = candidates.minByOrNull { it.connections.utilizationPercent }
            if (best != null) {
                Log.i(TAG, "All servers overloaded, selected least loaded: ${best.name} " +
                        "(${"%.1f".format(best.connections.utilizationPercent)}%)")
                return@withContext best
            }
        }

        // Select server with lowest score
        val best = scores.minByOrNull { it.score }
        if (best != null) {
            Log.i(
                TAG,
                "Selected best server: ${best.server.name} (${best.server.address}) - " +
                        "load=${"%.1f".format(best.server.connections.utilizationPercent)}%, " +
                        "latency=${best.latency}ms, score=${"%.1f".format(best.score)}"
            )
            return@withContext best.server
        }

        Log.w(TAG, "Failed to select best server, using first available")
        candidates.firstOrNull()
    }

    /**
     * Get optimal server with fallback
     * 1. Try to discover servers from API
     * 2. Select best server based on load and latency
     * 3. Fall back to default if discovery fails
     */
    suspend fun getOptimalServer(apiUrl: String, fallbackAddress: String): String {
        return try {
            val servers = discoverServers(apiUrl)
            val best = selectBestServer(servers)
            best?.address ?: fallbackAddress
        } catch (e: Exception) {
            Log.e(TAG, "Server discovery failed: ${e.message}, using fallback: $fallbackAddress", e)
            fallbackAddress
        }
    }
}
