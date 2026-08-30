package jp.okusuri.nonda.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "records", indices = [Index(value = ["date", "doseType"], unique = true)])
data class MedicationRecord(@PrimaryKey(autoGenerate = true) val id: Long = 0, val date: String, val doseType: String, val scheduledAt: String, val takenAt: String? = null, val status: String = "MISSED")

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

@Database(entities = [MedicationRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    companion object { @Volatile private var instance: AppDatabase? = null; fun get(c: android.content.Context) = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(c.applicationContext, AppDatabase::class.java, "nonda.db").build().also { instance = it } } }
}
