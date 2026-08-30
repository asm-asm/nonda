package jp.okusuri.nonda.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import jp.okusuri.nonda.R
import jp.okusuri.nonda.data.AppDatabase
import jp.okusuri.nonda.data.MedicationRecord
import jp.okusuri.nonda.data.SettingsStore
import jp.okusuri.nonda.notification.MedicationNotifications
import jp.okusuri.nonda.notification.day
import jp.okusuri.nonda.notification.now
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MedicationWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            updateAll(context, manager, ids)
            pending.finish()
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun refresh(context: Context) {
            scope.launch { refreshNow(context) }
        }

        suspend fun refreshNow(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, MedicationWidgetReceiver::class.java)
            )
            updateAll(context, manager, ids)
        }

        private suspend fun updateAll(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray
        ) {
            if (ids.isEmpty()) return

            val settings = SettingsStore(context).read()
            val records = AppDatabase.get(context).medicationDao().forDate(day())
            val morning = records.firstOrNull { it.doseType == "朝" }
            val evening = records.firstOrNull { it.doseType == "夜" }
            val current = now()
            val pendingType = when {
                current >= settings.evening && evening?.status != "TAKEN" -> "夜"
                morning?.status != "TAKEN" -> "朝"
                evening?.status != "TAKEN" -> "夜"
                else -> null
            }
            val status = statusText(
                morning,
                evening,
                settings.morning,
                settings.evening,
                current
            )
            ids.forEach { updateOne(context, manager, it, status, pendingType) }
        }

        private fun statusText(
            morning: MedicationRecord?,
            evening: MedicationRecord?,
            morningTime: String,
            eveningTime: String,
            current: String
        ): String {
            val morningLine = when {
                morning?.status == "TAKEN" -> "朝 ✓ ${morning.takenAt}"
                current >= morningTime -> "朝 ⚠ 飲み忘れ中"
                else -> "次は朝 $morningTime"
            }
            val eveningLine = when {
                evening?.status == "TAKEN" -> "夜 ✓ ${evening.takenAt}"
                morning?.status == "TAKEN" || current >= eveningTime -> "次は夜 $eveningTime"
                else -> null
            }
            return listOfNotNull(morningLine, eveningLine).joinToString("\n")
        }

        private fun updateOne(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            status: String,
            pendingType: String?
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_placeholder)
            val leftPadding = if (pendingType == null) 24 else 16
            views.setViewPadding(
                R.id.widget_content,
                dp(context, leftPadding),
                dp(context, 6),
                dp(context, 16),
                dp(context, 6)
            )
            views.setTextViewText(R.id.widget_title, "飲んだ？")
            views.setTextViewText(R.id.widget_status, status)
            if (pendingType == null) {
                views.setViewVisibility(R.id.widget_take, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_take, View.VISIBLE)
                views.setTextViewText(R.id.widget_take, "$pendingType　飲んだ")
                val intent = Intent(context, WidgetTakeActivity::class.java)
                    .setData(Uri.parse("nonda://widget/$id/$pendingType"))
                    .putExtra(MedicationNotifications.EXTRA_TYPE, pendingType)
                val click = PendingIntent.getActivity(
                    context,
                    id * 10 + pendingType.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_take, click)
            }
            manager.updateAppWidget(id, views)
        }

        private fun dp(context: Context, value: Int): Int =
            (value * context.resources.displayMetrics.density).toInt()
    }
}
