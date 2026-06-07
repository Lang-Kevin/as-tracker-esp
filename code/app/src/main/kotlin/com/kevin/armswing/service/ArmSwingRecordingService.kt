package com.kevin.armswing.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kevin.armswing.MainActivity
import com.kevin.armswing.ble.BleManager
import com.kevin.armswing.data.repository.SessionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ArmSwingRecordingService : Service() {

    @Inject lateinit var bleManager: BleManager
    @Inject lateinit var sessionRepository: SessionRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var notificationJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "arm_swing_recording"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.kevin.armswing.ACTION_START"
        const val ACTION_STOP  = "com.kevin.armswing.ACTION_STOP"
        const val EXTRA_LABEL  = "label"

        fun startIntent(context: Context, label: String) =
            Intent(context, ArmSwingRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LABEL, label)
            }

        fun stopIntent(context: Context) =
            Intent(context, ArmSwingRecordingService::class.java).apply { action = ACTION_STOP }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val label = intent.getStringExtra(EXTRA_LABEL) ?: "Training"
                startForeground(NOTIFICATION_ID, buildNotification("00:00"))
                startSession(label)
            }
            ACTION_STOP -> {
                stopSession()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startSession(label: String) {
        scope.launch {
            sessionRepository.startSession(label)
            val startMs = System.currentTimeMillis()
            notificationJob = launch {
                while (true) {
                    delay(1_000L)
                    val elapsed = (System.currentTimeMillis() - startMs) / 1000
                    updateNotification("%02d:%02d".format(elapsed / 60, elapsed % 60))
                }
            }
        }
    }

    private fun stopSession() {
        notificationJob?.cancel()
        scope.launch { sessionRepository.stopSession() }
    }

    private fun updateNotification(elapsed: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(elapsed))
    }

    private fun buildNotification(elapsed: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Arm Swing läuft")
            .setContentText(elapsed)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        NotificationChannel(CHANNEL_ID, "Arm Swing Aufzeichnung", NotificationManager.IMPORTANCE_LOW)
            .also { getSystemService(NotificationManager::class.java).createNotificationChannel(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
