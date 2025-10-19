package com.vyx.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Main API for Vyx SDK
 *
 * Example usage:
 * ```kotlin
 * // Initialize in your Application class
 * val config = VyxConfig(
 *     apiToken = "your_api_token_here",
 *     enableDebugLogging = BuildConfig.DEBUG
 * )
 * VyxNode.initialize(context, config)
 *
 * // Start earning (after user consent)
 * VyxNode.start()
 *
 * // Stop earning
 * VyxNode.stop()
 *
 * // Check status
 * if (VyxNode.isRunning()) {
 *     // Node is active
 * }
 * ```
 */
@SuppressLint("StaticFieldLeak") // Safe: we store applicationContext, not activity context
object VyxNode {

    private const val TAG = "VyxNode"

    private var context: Context? = null
    private var config: VyxConfig? = null
    private var isInitialized = false

    /**
     * Initialize the Vyx SDK
     * Call this in your Application.onCreate() or before using the SDK
     *
     * @param context Application context
     * @param config SDK configuration
     */
    fun initialize(context: Context, config: VyxConfig) {
        if (isInitialized) {
            Log.w(TAG, "VyxNode already initialized")
            return
        }

        this.context = context.applicationContext
        this.config = config
        isInitialized = true

        if (config.enableDebugLogging) {
            Log.i(TAG, "Vyx SDK initialized - Version: ${BuildConfig.SDK_VERSION}")
        }
    }

    /**
     * Start the Vyx node service
     * This will start earning rewards by sharing bandwidth
     *
     * @throws IllegalStateException if SDK is not initialized
     * @return true if service was started, false if already running
     */
    @Synchronized
    fun start(): Boolean {
        checkInitialized()

        // Prevent duplicate starts - check BEFORE and AFTER service start
        if (isRunning()) {
            if (config?.enableDebugLogging == true) {
                Log.w(TAG, "Vyx node service is already running, skipping duplicate start")
            }
            return false
        }

        val ctx = requireContext()
        val serviceIntent = Intent(ctx, VyxService::class.java).apply {
            putExtra(VyxService.EXTRA_CONFIG, config)
        }

        // Start the service
        ContextCompat.startForegroundService(ctx, serviceIntent)

        // Double-check: Brief delay to ensure service state is updated
        Thread.sleep(100)

        // Verify service actually started
        if (!isRunning()) {
            Log.e(TAG, "Vyx node service failed to start")
            return false
        }

        if (config?.enableDebugLogging == true) {
            Log.i(TAG, "Vyx node service started successfully")
        }
        return true
    }

    /**
     * Stop the Vyx node service
     * This will stop earning rewards
     *
     * @throws IllegalStateException if SDK is not initialized
     * @return true if service was stopped, false if not running
     */
    fun stop(): Boolean {
        checkInitialized()

        // Check if service is running
        if (!isRunning()) {
            Log.w("Vyx", "Vyx node service is not running")
            return false
        }

        val ctx = requireContext()
        val serviceIntent = Intent(ctx, VyxService::class.java)
        ctx.stopService(serviceIntent)
        if (config?.enableDebugLogging == true) {
            Log.i(TAG, "Vyx node service stopped")
        }
        return true
    }

    /**
     * Check if the Vyx node service is running
     *
     * @return true if service is active
     */
    fun isRunning(): Boolean {
        checkInitialized()
        return VyxService.isServiceRunning()
    }

    /**
     * Get current SDK version
     */
    fun getVersion(): String = BuildConfig.SDK_VERSION

    /**
     * Get current configuration
     *
     * @return Current VyxConfig or null if not initialized
     */
    fun getConfig(): VyxConfig? = config

    private fun checkInitialized() {
        check(isInitialized) {
            "VyxNode is not initialized. Call VyxNode.initialize() first."
        }
    }

    private fun requireContext(): Context {
        return checkNotNull(context) {
            "Context is null. VyxNode may not be properly initialized."
        }
    }
}
