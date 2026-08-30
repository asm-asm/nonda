package jp.okusuri.nonda.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import jp.okusuri.nonda.BuildConfig
import jp.okusuri.nonda.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class AvailableUpdate(
    val version: String,
    val releaseUrl: String,
)

sealed interface UpdateCheckResult {
    data class Available(val update: AvailableUpdate) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object Error : UpdateCheckResult
}

object AppUpdateChecker {
    private const val RELEASE_API = "https://api.github.com/repos/asm-asm/nonda/releases/latest"
    private const val PREFS = "app_update"
    private const val LAST_CHECK = "last_check"
    private const val LATEST_VERSION = "latest_version"
    private const val LATEST_URL = "latest_url"
    private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

    suspend fun check(context: Context, force: Boolean = false): UpdateCheckResult = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(LAST_CHECK, 0L) < CHECK_INTERVAL_MS) {
            return@withContext cachedResult(context)
        }

        try {
            val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "nonda-android/${BuildConfig.VERSION_NAME}")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext cachedAvailable(context) ?: UpdateCheckResult.Error
                }
                val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                val version = json.getString("tag_name").trim().removePrefix("v")
                val releaseUrl = json.getString("html_url")
                prefs.edit()
                    .putLong(LAST_CHECK, now)
                    .putString(LATEST_VERSION, version)
                    .putString(LATEST_URL, releaseUrl)
                    .apply()
                resultFor(version, releaseUrl)
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            cachedAvailable(context) ?: UpdateCheckResult.Error
        }
    }

    private fun cachedResult(context: Context): UpdateCheckResult =
        cachedAvailable(context) ?: UpdateCheckResult.UpToDate

    private fun cachedAvailable(context: Context): UpdateCheckResult.Available? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = prefs.getString(LATEST_VERSION, null) ?: return null
        val url = prefs.getString(LATEST_URL, null) ?: return null
        return resultFor(version, url) as? UpdateCheckResult.Available
    }

    private fun resultFor(version: String, url: String): UpdateCheckResult =
        if (isNewer(version, BuildConfig.VERSION_NAME)) {
            UpdateCheckResult.Available(AvailableUpdate(version, url))
        } else {
            UpdateCheckResult.UpToDate
        }

    internal fun isNewer(latest: String, current: String): Boolean {
        val latestParts = versionParts(latest)
        val currentParts = versionParts(current)
        val size = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until size) {
            val latestPart = latestParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    private fun versionParts(version: String): List<Int> =
        Regex("\\d+").findAll(version).map { it.value.toIntOrNull() ?: 0 }.toList()
}

object AppUpdateScheduler {
    private const val WORK_NAME = "app-update-check"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

class AppUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (val result = AppUpdateChecker.check(applicationContext, force = true)) {
        is UpdateCheckResult.Available -> {
            AppUpdateNotifier.show(applicationContext, result.update)
            Result.success()
        }
        UpdateCheckResult.UpToDate -> Result.success()
        UpdateCheckResult.Error -> Result.success()
    }
}

object AppUpdateNotifier {
    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 4101
    private const val PREFS = "app_update"
    private const val LAST_NOTIFIED = "last_notified"

    fun show(context: Context, update: AvailableUpdate) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(LAST_NOTIFIED, null) == update.version) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "アプリの更新", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "新しいバージョンが公開されたときに知らせます"
            },
        )
        val openRelease = PendingIntent.getActivity(
            context,
            0,
            Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("「飲んだ？」を更新できます")
            .setContentText("新しいバージョン ${update.version} が公開されました")
            .setContentIntent(openRelease)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        prefs.edit().putString(LAST_NOTIFIED, update.version).apply()
    }
}
