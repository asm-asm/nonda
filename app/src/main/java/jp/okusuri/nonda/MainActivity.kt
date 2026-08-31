package jp.okusuri.nonda

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import jp.okusuri.nonda.data.*
import jp.okusuri.nonda.notification.*
import jp.okusuri.nonda.update.AppUpdateChecker
import jp.okusuri.nonda.update.AppUpdateScheduler
import jp.okusuri.nonda.update.AvailableUpdate
import jp.okusuri.nonda.update.UpdateCheckResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val openedAlarm = MutableStateFlow<String?>(null)
    override fun onResume() { super.onResume(); MedicationScheduler.schedule(this); jp.okusuri.nonda.widget.MedicationWidgetReceiver.refresh(this) }
    override fun onCreate(b: Bundle?) { super.onCreate(b); handleAlarmIntent(intent); if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS); MedicationNotifications.channel(this); MedicationScheduler.schedule(this); AppUpdateScheduler.schedule(this); setContent { NondaApp() } }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleAlarmIntent(intent) }

    private fun handleAlarmIntent(source: Intent?) {
        if (source?.action != MedicationNotifications.ACTION_OPEN_ALARM) return
        openedAlarm.value = source.getStringExtra(MedicationNotifications.EXTRA_TYPE)
        source.action = null
        source.removeExtra(MedicationNotifications.EXTRA_TYPE)
    }

    @Composable fun NondaApp() {
        val db = remember { AppDatabase.get(this) }
        val settingsStore = remember { SettingsStore(this) }
        val records by db.medicationDao().all().collectAsState(emptyList())
        val notificationEvents by db.notificationEventDao().all().collectAsState(emptyList())
        val settings by settingsStore.flow.collectAsState()
        val openedAlarmType by openedAlarm.collectAsState()
        var tab by remember { mutableIntStateOf(0) }
        var availableUpdate by remember { mutableStateOf<AvailableUpdate?>(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            val result = AppUpdateChecker.check(this@MainActivity)
            if (result is UpdateCheckResult.Available) availableUpdate = result.update
        }
        LaunchedEffect(openedAlarmType) { if (openedAlarmType != null) tab = 0 }

        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
            Scaffold(bottomBar = { NavigationBar { listOf("今日" to Icons.Default.Today, "履歴" to Icons.Default.History, "設定" to Icons.Default.Settings).forEachIndexed { i, x -> NavigationBarItem(tab == i, { tab = i }, icon = { Icon(x.second, null) }, label = { Text(x.first) }) } } }) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    when (tab) {
                        0 -> Today(records, settings, take = { r -> scope.launch { recordTaken(this@MainActivity, r.doseType); MedicationNotifications.cancel(this@MainActivity, r.doseType); jp.okusuri.nonda.widget.MedicationWidgetReceiver.refresh(this@MainActivity) } }, undo = { r -> scope.launch { db.medicationDao().undoTake(r.id); jp.okusuri.nonda.widget.MedicationWidgetReceiver.refresh(this@MainActivity) } })
                        1 -> History(records, notificationEvents, db)
                        else -> Settings(settings, settingsStore) { availableUpdate = it }
                    }
                }
            }
            if (openedAlarmType == null) availableUpdate?.let { update ->
                AlertDialog(
                    onDismissRequest = { availableUpdate = null },
                    title = { Text("新しいバージョンがあります") },
                    text = { Text("「飲んだ？」${update.version} をダウンロードできます。") },
                    confirmButton = {
                        Button(onClick = {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl)))
                            availableUpdate = null
                        }) { Text("更新ページを開く") }
                    },
                    dismissButton = { TextButton(onClick = { availableUpdate = null }) { Text("あとで") } },
                )
            }
            openedAlarmType?.let { type ->
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("${type}のお薬の時間です") },
                    text = { Text("通知音を止めるか、飲んだ記録を付けてください。") },
                    confirmButton = {
                        Button(onClick = {
                            scope.launch {
                                recordTaken(this@MainActivity, type)
                                MedicationNotifications.cancel(this@MainActivity, type)
                                jp.okusuri.nonda.widget.MedicationWidgetReceiver.refresh(this@MainActivity)
                                openedAlarm.value = null
                            }
                        }) { Text("飲んだ") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = {
                            MedicationNotifications.cancel(this@MainActivity, type)
                            openedAlarm.value = null
                        }) { Text("音を止める") }
                    },
                )
            }
        }
    }
    @Composable private fun Today(records: List<MedicationRecord>, s: AppSettings, take: (MedicationRecord) -> Unit, undo: (MedicationRecord) -> Unit) { val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()); val today = records.filter { it.date == date }; LaunchedEffect(date, s.morning, s.evening) { val dao = AppDatabase.get(this@MainActivity).medicationDao(); dao.ensure(date, "朝", s.morning); dao.ensure(date, "夜", s.evening) }; LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { item { Text("飲んだ？", style = MaterialTheme.typography.displaySmall); Text(SimpleDateFormat("M月d日（E）", Locale.JAPAN).format(Date()), style = MaterialTheme.typography.titleMedium) }; items(today) { r -> val taken = r.status == "TAKEN"; Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text(r.doseType, style = MaterialTheme.typography.titleLarge); Text(r.scheduledAt, style = MaterialTheme.typography.displayMedium); if (taken) { Text("✓ 飲んだ ${r.takenAt}", color = MaterialTheme.colorScheme.primary); OutlinedButton({ undo(r) }, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("飲んだ記録を取り消す") } } else { Text(if (r.scheduledAt <= now()) "⚠ 飲み忘れ中" else "次は ${r.scheduledAt}"); Button({ take(r) }, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("飲んだ") } } } } }; item { Text("服用方法については医師・薬剤師の指示に従ってください", style = MaterialTheme.typography.bodySmall) } } }
    @Composable private fun History(records: List<MedicationRecord>, notificationEvents: List<NotificationEvent>, db: AppDatabase) {
        val scope = rememberCoroutineScope()
        LazyColumn(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("履歴", style = MaterialTheme.typography.headlineMedium) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("通知履歴", style = MaterialTheme.typography.titleLarge)
                    if (notificationEvents.isNotEmpty()) {
                        TextButton(onClick = { scope.launch { db.notificationEventDao().clear() } }) { Text("消去") }
                    }
                }
            }
            if (notificationEvents.isEmpty()) {
                item { Text("通知履歴はまだありません", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(notificationEvents, key = { "notification-${it.id}" }) { event ->
                    val resultText = when (event.result) {
                        "POSTED" -> "通知しました"
                        "BLOCKED" -> "通知が許可されていません"
                        else -> "通知に失敗しました"
                    }
                    Card(Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("${event.date}　${event.notifiedAt}") },
                            supportingContent = { Text("${event.doseType}　$resultText") },
                            leadingContent = { Icon(Icons.Default.Notifications, null) },
                        )
                    }
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Text("服薬記録", style = MaterialTheme.typography.titleLarge) }
            items(records, key = { "record-${it.id}" }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("${r.date}　${r.doseType}") },
                        supportingContent = { Text("予定 ${r.scheduledAt}　${if (r.status == "TAKEN") "✓ ${r.takenAt}" else "飲み忘れ"}") },
                        trailingContent = { IconButton({ scope.launch { db.medicationDao().delete(r.id) } }) { Icon(Icons.Default.Delete, "記録を削除") } },
                    )
                }
            }
        }
    }
    @Suppress("DEPRECATION")
    @Composable private fun Settings(s: AppSettings, store: SettingsStore, onUpdateFound: (AvailableUpdate) -> Unit) {
        var name by remember(s) { mutableStateOf(s.name) }
        var morning by remember(s) { mutableStateOf(s.morning) }
        var evening by remember(s) { mutableStateOf(s.evening) }
        var sound by remember(s) { mutableStateOf(s.soundEnabled) }
        var vibration by remember(s) { mutableStateOf(s.vibrationEnabled) }
        var soundUri by remember(s) { mutableStateOf(s.notificationSoundUri) }
        var updateStatus by remember { mutableStateOf<String?>(null) }
        var checkingUpdate by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                soundUri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.toString()
            }
        }
        val soundTitle = remember(soundUri) {
            if (soundUri == ZUNDAMON_SOUND_URI) "ずんだもん「お薬飲んでね」"
            else soundUri?.let { value -> runCatching { RingtoneManager.getRingtone(this@MainActivity, Uri.parse(value))?.getTitle(this@MainActivity) }.getOrNull() } ?: "端末の標準音"
        }
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("設定", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(name, { name = it }, label = { Text("薬の名前") })
            OutlinedTextField(morning, { morning = it }, label = { Text("朝の服薬時刻（HH:mm）") })
            OutlinedTextField(evening, { evening = it }, label = { Text("夜の服薬時刻（HH:mm）") })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("通知音"); Text(if (sound) "音を鳴らす" else "音を鳴らさない", style = MaterialTheme.typography.bodySmall) }; Switch(sound, { sound = it }) }
            OutlinedButton({ picker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION).putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false).putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, soundUri?.let(Uri::parse))) }, Modifier.fillMaxWidth(), enabled = sound) { Text("通知音を選ぶ：$soundTitle") }
            OutlinedButton({ soundUri = ZUNDAMON_SOUND_URI }, Modifier.fillMaxWidth(), enabled = sound) { Text("ずんだもん音声を使う") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("振動"); Text(if (vibration) "振動させる" else "振動させない", style = MaterialTheme.typography.bodySmall) }; Switch(vibration, { vibration = it }) }
            Button({ store.save(s.copy(name = name, morning = morning, evening = evening, soundEnabled = sound, vibrationEnabled = vibration, notificationSoundUri = soundUri)); MedicationNotifications.channel(this@MainActivity); MedicationScheduler.schedule(this@MainActivity) }, Modifier.fillMaxWidth()) { Text("保存") }
            OutlinedButton({ store.save(s.copy(name = name, morning = morning, evening = evening, soundEnabled = sound, vibrationEnabled = vibration, notificationSoundUri = soundUri)); MedicationNotifications.preview(this@MainActivity) }, Modifier.fillMaxWidth()) { Text("通知音を試す") }
            HorizontalDivider()
            Text("アプリの更新", style = MaterialTheme.typography.titleMedium)
            Text("現在のバージョン：${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = {
                    checkingUpdate = true
                    updateStatus = null
                    scope.launch {
                        when (val result = AppUpdateChecker.check(this@MainActivity, force = true)) {
                            is UpdateCheckResult.Available -> {
                                updateStatus = "新しいバージョン ${result.update.version} があります"
                                onUpdateFound(result.update)
                            }
                            UpdateCheckResult.UpToDate -> updateStatus = "このアプリは最新版です"
                            UpdateCheckResult.Error -> updateStatus = "更新情報を確認できませんでした"
                        }
                        checkingUpdate = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !checkingUpdate,
            ) { Text(if (checkingUpdate) "確認中…" else "今すぐ更新を確認") }
            updateStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("音声：VOICEVOX:ずんだもん", style = MaterialTheme.typography.bodySmall)
            Text("通知は端末の設定から許可してください。通知が届かない場合は、バッテリー最適化の対象外に設定してください。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
