package com.nathan.workouttracker

import App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private val container get() = (application as WorkoutApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App(container) }
    }

    override fun onResume() {
        super.onResume()
        // Pick up workouts logged on the PC when coming back to the app.
        container.syncManager.requestSync()
    }
}
