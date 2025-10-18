package com.vyx.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.vyx.sdk.VyxNode

/**
 * Example activity demonstrating Vyx SDK usage
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }

        // Set up click listeners
        startButton.setOnClickListener {
            onStartClicked()
        }

        stopButton.setOnClickListener {
            onStopClicked()
        }

        // Update UI state
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun onStartClicked() {
        try {
            // In a real app, you would:
            // 1. Show consent dialog explaining bandwidth sharing
            // 2. Get user agreement
            // 3. Only then start the node

            val started = VyxNode.start()
            if (started) {
                Toast.makeText(this, "Node started successfully", Toast.LENGTH_SHORT).show()

                // Delay UI update slightly to allow service to initialize
                startButton.postDelayed({
                    updateStatus()
                }, 300)
            } else {
                Toast.makeText(this, "Node is already running", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Error starting node: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun onStopClicked() {
        try {
            val stopped = VyxNode.stop()
            if (stopped) {
                Toast.makeText(this, "Node stopped", Toast.LENGTH_SHORT).show()

                // Delay UI update slightly to allow service to stop
                stopButton.postDelayed({
                    updateStatus()
                }, 300)
            } else {
                Toast.makeText(this, "Node is not running", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Error stopping node: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateStatus() {
        val isRunning = VyxNode.isRunning()
        val version = VyxNode.getVersion()

        statusText.text = """
            SDK Version: $version
            Status: ${if (isRunning) "Running" else "Stopped"}

            ${if (isRunning) "✅ Node is active" else "⏸️ Node is stopped"}
        """.trimIndent()

        startButton.isEnabled = !isRunning
        stopButton.isEnabled = isRunning
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Notification permission denied - service may not work properly",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
