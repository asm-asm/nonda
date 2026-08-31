package jp.okusuri.nonda.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "records", indices = [Index(value = ["date", "doseType"], unique = true)])
data class MedicationRecord(@PrimaryKey(autoGenerate = true) val id: Long = 0, val date: String, val doseType: String, val scheduledAt: String, val takenAt: String? = null, val status: String = "MISSED")

@Entity(tableName = "notification_events")
data class NotificationEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val doseType: String,
    val notifiedAt: String,
    val result: String,
)

@Dao
interface MedicationDao {
    @Query("SELECT * FROM records ORDER BY date DESC, scheduledAt ASC") fun all(): Flow<List<MedicationRecord>>
    @Query("SELECT * FROM records WHERE date = :date") suspend fun forDate(date: String): List<MedicationRecord>
    @Query("INSERT OR IGNORE INTO records (date, doseType, scheduledAt, takenAt, status) VALUES (:date, :doseType, :scheduledAt, NULL, 'MISSED')") suspend fun ensure(date: String, doseType: String, scheduledAt: String)
    @Query("UPDATE records SET takenAt = :takenAt, status = 'TAKEN' WHERE date = :date AND doseType = :doseType") suspend fun takeForDate(date: String, doseType: String, takenAt: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(record: MedicationRecord)
    @Query("DELETE FROM records WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE records SET takenAt = :takenAt, status = 'TAKEN' WHERE id = :id") suspend fun take(id: Long, takenAt: String)
    @Query("UPDATE records SET takenAt = NULL, status = 'MISSED' WHERE id = :id") suspend fun undoTake(id: Long)
    @Query("UPDATE records SET takenAt = NULL, status = 'MISSED' WHERE id = :id") suspend fun undo(id: Long)
    @Query("UPDATE records SET scheduledAt = :time WHERE id = :id") suspend fun changeTime(id: Long, time: String)
}

@Dao
interface NotificationEventDao {
    @Query("SELECT * FROM notification_events ORDER BY id DESC LIMIT 100")
    fun all(): Flow<List<NotificationEvent>>

    @Insert
    suspend fun add(event: NotificationEvent)

    @Query("DELETE FROM notification_events")
    suspend fun clear()
}

@Database(entities = [MedicationRecord::class, NotificationEvent::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun notificationEventDao(): NotificationEventDao

    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS notification_events (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "date TEXT NOT NULL, doseType TEXT NOT NULL, " +
                        "notifiedAt TEXT NOT NULL, result TEXT NOT NULL)"
                )
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(c: android.content.Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                c.applicationContext,
                AppDatabase::class.java,
                "nonda.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
