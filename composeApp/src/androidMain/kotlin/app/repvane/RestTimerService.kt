package app.repvane

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import ui.RestNotifier

/**
 * Foreground service that shows the rest countdown on the lock screen / notification shade and,
 * when rest is over, vibrates and posts a "Rest done" notification with sound. Skips the alert
 * if the app is open (the app shows its own green glow and vibrates).
 */
class RestTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private val finish = Runnable { onRestDone() }
    private var title = "Rest"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            stop()
            return START_NOT_STICKY
        }
        val endAt = intent?.getLongExtra(EXTRA_END_AT, 0L) ?: 0L
        title = intent?.getStringExtra(EXTRA_TITLE) ?: "Rest"
        val remaining = endAt - System.currentTimeMillis()
        createChannels(this)
        // A service started with startForegroundService() must always call startForeground().
        val notification = countdownNotification(endAt)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(COUNTDOWN_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(COUNTDOWN_ID, notification)
        }
        getSystemService(NotificationManager::class.java).cancel(DONE_ID)
        if (remaining <= 0) {
            stop()
            return START_NOT_STICKY
        }

        // Keep the CPU awake just long enough to fire the alert while the screen is off.
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Repvane:rest")
            .apply { acquire(remaining + 10_000) }
        handler.removeCallbacks(finish)
        handler.postDelayed(finish, remaining)
        return START_NOT_STICKY
    }

    private fun countdownNotification(endAt: Long): Notification =
        Notification.Builder(this, CHANNEL_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Resting · $title")
            .setContentText("Next set when the timer hits 0")
            .setWhen(endAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent(this))
            .build()

    private fun onRestDone() {
        if (!WorkoutApplication.isInForeground) {
            vibrate()
            val done = Notification.Builder(this, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Rest done, go!")
                .setContentText(title)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setTimeoutAfter(5 * 60_000L)
                .setContentIntent(openAppIntent(this))
                .build()
            getSystemService(NotificationManager::class.java).notify(DONE_ID, done)
        }
        stop()
    }

    private fun vibrate() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 300), -1))
    }

    private fun stop() {
        handler.removeCallbacks(finish)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(finish)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_COUNTDOWN = "rest_countdown"
        private const val CHANNEL_DONE = "rest_done_v1"
        private const val COUNTDOWN_ID = 1
        private const val DONE_ID = 2
        private const val EXTRA_END_AT = "end_at"
        private const val EXTRA_TITLE = "title"
        private const val ACTION_CANCEL = "cancel"

        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_COUNTDOWN, "Rest countdown", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows the rest timer between sets"
                    setShowBadge(false)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, "Rest done", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alerts you when rest between sets is over"
                    // Vibration is done by the service, so it also works if notifications are blocked.
                    enableVibration(false)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                }
            )
        }

        private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** [RestNotifier] that drives this service. */
        fun notifier(context: Context): RestNotifier = object : RestNotifier {
            private val app = context.applicationContext
            private var current: Long? = null

            override fun start(endAt: Long, title: String) {
                if (current == endAt || endAt <= System.currentTimeMillis()) return
                current = endAt
                val intent = Intent(app, RestTimerService::class.java)
                    .putExtra(EXTRA_END_AT, endAt)
                    .putExtra(EXTRA_TITLE, title)
                // Only called while the app is open, which allows starting a foreground service.
                runCatching { app.startForegroundService(intent) }
            }

            override fun cancel() {
                if (current == null) return
                current = null
                runCatching { app.startService(Intent(app, RestTimerService::class.java).setAction(ACTION_CANCEL)) }
            }
        }
    }
}
