package com.kevin.armswing.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.kevin.armswing.MainActivity
import com.kevin.armswing.data.repository.SessionRepository
import com.kevin.shared.service.BaseRecordingService
import com.kevin.shared.service.RecordingServiceContract
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ArmSwingRecordingService : BaseRecordingService() {

    @Inject lateinit var sessionRepository: SessionRepository

    private var notificationJob: Job? = null
    private var sessionLabel: String = "Training"

    override val notificationChannelId = "arm_swing_recording"
    override val notificationChannelName = "Arm Swing Aufzeichnung"
    override val notificationId = 1
    @Suppress("InlinedApi")
    override val fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE

    override fun buildNotification(label: String, elapsed: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Arm Swing lÃ¤uft")
            .setContentText("$label â€¢ $elapsed")
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    override suspend fun onRecordingStart(label: String) {
        sessionLabel = label
        sessionRepository.startSession(label)
        val startMs = System.currentTimeMillis()
        notificationJob = serviceScope.launch {
            while (true) {
                delay(1_000L)
                val elapsed = (System.currentTimeMillis() - startMs) / 1000
                updateNotification(sessionLabel, "%02d:%02d".format(elapsed / 60, elapsed % 60))
            }
        }
    }

    override suspend fun onRecordingStop() {
        notificationJob?.cancel()
        sessionRepository.stopSession()
    }

    companion object {
        fun startIntent(context: Context, label: String) =
            RecordingServiceContract.startIntent<ArmSwingRecordingService>(context, label)

        fun stopIntent(context: Context) =
            RecordingServiceContract.stopIntent<ArmSwingRecordingService>(context)
    }
}
