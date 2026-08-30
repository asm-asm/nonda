package jp.okusuri.nonda.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import jp.okusuri.nonda.notification.MedicationNotifications
import jp.okusuri.nonda.notification.recordTaken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetTakeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getStringExtra(MedicationNotifications.EXTRA_TYPE)
        if (type == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                recordTaken(this@WidgetTakeActivity, type)
                MedicationNotifications.cancel(this@WidgetTakeActivity, type)
                MedicationWidgetReceiver.refreshNow(this@WidgetTakeActivity)
            }
            finish()
        }
    }
}
