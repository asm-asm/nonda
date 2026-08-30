package jp.okusuri.nonda.notification

import android.app.*
import android.content.*
import android.os.Build
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import jp.okusuri.nonda.data.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

object MedicationNotifications {
    const val ACTION_TAKE = "jp.okusuri.nonda.TAKE"; const val ACTION_STOP_SOUND = "jp.okusuri.nonda.STOP_SOUND"; const val EXTRA_TYPE = "type"
    internal fun soundUri(s: AppSettings) = Uri.parse(s.notificationSoundUri ?: ZUNDAMON_SOUND_URI)
    private fun channelId(s: AppSettings) = "medication_${if (s.soundEnabled) Integer.toHexString(soundUri(s).toString().hashCode()) else "silent"}_${if (s.vibrationEnabled) "vibrate" else "still"}"
    fun channel(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val s = SettingsStore(c).read()
        val channel = NotificationChannel(channelId(s), "服薬リマインダー", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "服薬時刻のお知らせ"
            enableVibration(s.vibrationEnabled)
            if (s.soundEnabled) {
                val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build()
                setSound(soundUri(s), audio)
            } else setSound(null, null)
        }
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    fun notify(c: Context, type: String) {
        val s = SettingsStore(c).read()
        if (!s.notifications) return
        channel(c)
        val pi = PendingIntent.getBroadcast(c, type.hashCode(), Intent(c, MedicationReceiver::class.java).setAction(ACTION_TAKE).putExtra(EXTRA_TYPE, type), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopPi = PendingIntent.getBroadcast(c, (type + "stop").hashCode(), Intent(c, MedicationReceiver::class.java).setAction(ACTION_STOP_SOUND).putExtra(EXTRA_TYPE, type), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val defaults = (if (s.soundEnabled) NotificationCompat.DEFAULT_SOUND else 0) or (if (s.vibrationEnabled) NotificationCompat.DEFAULT_VIBRATE else 0)
        NotificationManagerCompat.from(c).notify(type.hashCode(), NotificationCompat.Builder(c, channelId(s)).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("薬の時間です").setContentText("${type}の${s.name}、飲んだ？").setPriority(NotificationCompat.PRIORITY_HIGH).setDefaults(defaults and NotificationCompat.DEFAULT_VIBRATE).setSound(null).setAutoCancel(false).setContentIntent(stopPi).addAction(0, "飲んだ", pi).addAction(0, "音を止める", stopPi).build())
        if (s.soundEnabled) {
            val serviceIntent = Intent(c, MedicationAlarmService::class.java).putExtra(EXTRA_TYPE, type)
            if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(serviceIntent) else c.startService(serviceIntent)
        }
    }
    fun preview(c: Context) {
        val s = SettingsStore(c).read()
        channel(c)
        val defaults = if (s.vibrationEnabled) NotificationCompat.DEFAULT_VIBRATE else 0
        NotificationManagerCompat.from(c).notify(9090, NotificationCompat.Builder(c, channelId(s))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("通知音のテスト")
            .setContentText("お薬飲んでね")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(defaults)
            .setSound(if (s.soundEnabled) soundUri(s) else null)
            .setAutoCancel(true)
            .build())
    }
    fun cancel(c: Context, type: String) { c.stopService(Intent(c, MedicationAlarmService::class.java)); NotificationManagerCompat.from(c).cancel(type.hashCode()) }
}
class MedicationReceiver : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { val type = i.getStringExtra(MedicationNotifications.EXTRA_TYPE) ?: return; if (i.action == MedicationNotifications.ACTION_STOP_SOUND) { MedicationNotifications.cancel(c, type); return }; if (i.action != MedicationNotifications.ACTION_TAKE) return; val p = goAsync(); CoroutineScope(Dispatchers.IO).launch { val dao = AppDatabase.get(c).medicationDao(); val s = SettingsStore(c).read(); val date = day(); dao.ensure(date, type, if (type == "朝") s.morning else s.evening); dao.takeForDate(date, type, now()); MedicationNotifications.cancel(c, type); jp.okusuri.nonda.widget.MedicationWidgetReceiver.refreshNow(c); p.finish() } } }
 suspend fun recordTaken(c: Context, type: String) { val dao = AppDatabase.get(c).medicationDao(); val s = SettingsStore(c).read(); val date = day(); dao.ensure(date, type, if (type == "朝") s.morning else s.evening); dao.takeForDate(date, type, now()) }
fun day() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
fun now() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
class BootReceiver : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { MedicationScheduler.schedule(c) } }
object MedicationScheduler { fun schedule(c: Context) { val s = SettingsStore(c).read(); val am = c.getSystemService(AlarmManager::class.java); listOf("朝" to s.morning, "夜" to s.evening).forEach { (type, time) -> val a = time.split(":"); val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, a[0].toInt()); set(Calendar.MINUTE, a[1].toInt()); set(Calendar.SECOND, 0); if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DATE, 1) }; val pi = PendingIntent.getBroadcast(c, type.hashCode(), Intent(c, AlarmReceiver::class.java).putExtra("type", type), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi) else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi) } } }
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val type = i.getStringExtra("type") ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alreadyTaken = AppDatabase.get(c).medicationDao().forDate(day())
                    .any { it.doseType == type && it.status == "TAKEN" }
                if (!alreadyTaken) {
                    MedicationNotifications.notify(c, type)
                    ReminderReceiver.schedule(c, type)
                }
                MedicationScheduler.schedule(c)
            } finally {
                pending.finish()
            }
        }
    }
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { val type = i.getStringExtra("type") ?: return; val p = goAsync(); CoroutineScope(Dispatchers.IO).launch { val r = AppDatabase.get(c).medicationDao().forDate(day()).firstOrNull { it.doseType == type }; if (r?.status == "TAKEN") { MedicationNotifications.cancel(c, type) } else { MedicationNotifications.notify(c, type); schedule(c, type) }; p.finish() } }
    companion object { fun schedule(c: Context, type: String) { val minutes = SettingsStore(c).read().interval.coerceAtLeast(1); val pi = PendingIntent.getBroadcast(c, (type + "repeat").hashCode(), Intent(c, ReminderReceiver::class.java).putExtra("type", type), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); c.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60_000L, pi) } }
}
