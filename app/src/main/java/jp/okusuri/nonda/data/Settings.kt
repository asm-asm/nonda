package jp.okusuri.nonda.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

const val ZUNDAMON_SOUND_URI = "android.resource://jp.okusuri.nonda/raw/zundamon_medication"

data class AppSettings(
    val name: String = "ジエノゲスト",
    val morning: String = "08:00",
    val evening: String = "20:00",
    val interval: Int = 10,
    val notifications: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val notificationSoundUri: String? = ZUNDAMON_SOUND_URI,
)

class SettingsStore(c: Context) {
    private val p = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    init {
        if (!p.getBoolean("zundamonSoundIntroduced", false)) {
            p.edit()
                .putString("notificationSoundUri", ZUNDAMON_SOUND_URI)
                .putBoolean("soundEnabled", true)
                .putBoolean("zundamonSoundIntroduced", true)
                .apply()
        }
    }
    private val state = MutableStateFlow(read())
    val flow = state.asStateFlow()

    fun read() = AppSettings(
        name = p.getString("name", "ジエノゲスト")!!,
        morning = p.getString("morning", "08:00")!!,
        evening = p.getString("evening", "20:00")!!,
        interval = p.getInt("interval", 10),
        notifications = p.getBoolean("notifications", true),
        soundEnabled = p.getBoolean("soundEnabled", true),
        vibrationEnabled = p.getBoolean("vibrationEnabled", true),
        notificationSoundUri = p.getString("notificationSoundUri", ZUNDAMON_SOUND_URI),
    )

    fun save(s: AppSettings) {
        p.edit()
            .putString("name", s.name)
            .putString("morning", s.morning)
            .putString("evening", s.evening)
            .putInt("interval", s.interval)
            .putBoolean("notifications", s.notifications)
            .putBoolean("soundEnabled", s.soundEnabled)
            .putBoolean("vibrationEnabled", s.vibrationEnabled)
            .putString("notificationSoundUri", s.notificationSoundUri)
            .apply()
        state.value = s
    }
}
