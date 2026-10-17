package com.nathan.workouttracker

import App
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private val container get() = (application as WorkoutApplication).container
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Needed for the rest timer notification (Android 13+).
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { App(container) }
    }

    override fun onStart() {
        super.onStart()
        WorkoutApplication.isInForeground = true
    }

    override fun onResume() {
        super.onResume()
        // Pick up workouts logged on the PC when coming back to the app.
        container.syncManager.requestSync()
    }

    override fun onStop() {
        WorkoutApplication.isInForeground = false
        super.onStop()
    }
}
