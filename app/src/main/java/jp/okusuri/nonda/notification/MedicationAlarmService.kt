package jp.okusuri.nonda.notification

import android.app.Service
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import jp.okusuri.nonda.data.SettingsStore

class MedicationAlarmService : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val replay = Runnable {
        player?.let { current ->
            runCatching {
                current.seekTo(0)
                current.start()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = intent?.getStringExtra(MedicationNotifications.EXTRA_TYPE) ?: return START_NOT_STICKY
        val settings = SettingsStore(this).read()
        startForeground(type.hashCode(), alarmNotification(type, settings))
        handler.removeCallbacks(replay)
        player?.release()
        player = null
        if (!settings.soundEnabled) return START_NOT_STICKY

        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@MedicationAlarmService, MedicationNotifications.soundUri(settings))
            isLooping = false
            setOnPreparedListener { it.start() }
            setOnCompletionListener { handler.postDelayed(replay, REPLAY_DELAY_MS) }
            prepareAsync()
        }
        return START_NOT_STICKY
    }

    private fun alarmNotification(type: String, settings: jp.okusuri.nonda.data.AppSettings) =
        NotificationCompat.Builder(this, channelId(settings))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("薬の時間です")
            .setContentText("${type}の${settings.name}、飲んだ？")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(stopIntent(type))
            .addAction(0, "飲んだ", takeIntent(type))
            .addAction(0, "音を止める", stopIntent(type))
            .build()

    private fun takeIntent(type: String) = PendingIntent.getBroadcast(
        this,
        type.hashCode(),
        Intent(this, MedicationReceiver::class.java)
            .setAction(MedicationNotifications.ACTION_TAKE)
            .putExtra(MedicationNotifications.EXTRA_TYPE, type),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun stopIntent(type: String) = PendingIntent.getBroadcast(
        this,
        (type + "stop").hashCode(),
        Intent(this, MedicationReceiver::class.java)
            .setAction(MedicationNotifications.ACTION_STOP_SOUND)
            .putExtra(MedicationNotifications.EXTRA_TYPE, type),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun channelId(settings: jp.okusuri.nonda.data.AppSettings) =
        "medication_${if (settings.soundEnabled) Integer.toHexString(MedicationNotifications.soundUri(settings).toString().hashCode()) else "silent"}_${if (settings.vibrationEnabled) "vibrate" else "still"}"

    override fun onDestroy() {
        handler.removeCallbacks(replay)
        player?.stopSafely()
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun MediaPlayer.stopSafely() {
        runCatching {
            if (isPlaying) stop()
        }
    }

    companion object {
        private const val REPLAY_DELAY_MS = 5_000L
    }
}
