package tech.shaffersoftworks.remotescreens.player.data.diagnostics

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage

internal const val MAX_STORED_LOGS = 1_000

/** Event IDs of recovered process exits, which [LogDao.trim] keeps ahead of ordinary entries. */
internal const val PROCESS_EXIT_EVENT_PREFIX = "exit:"

@Entity(tableName = "pending_logs", indices = [Index(value = ["eventId"], unique = true)])
data class StoredLog(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val eventId: String,
    val timestampMs: Long,
    val level: String,
    val tag: String,
    val message: String,
) {
    fun toMessage() = PlayerMessage.AppLog(
        timestampMs = timestampMs,
        level = level,
        tag = tag,
        message = message,
        eventId = eventId,
    )
}

@Dao
abstract class LogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(entry: StoredLog)

    @Query(
        "DELETE FROM pending_logs WHERE sequence NOT IN " +
            "(SELECT sequence FROM pending_logs " +
            "ORDER BY (eventId LIKE '$PROCESS_EXIT_EVENT_PREFIX%') DESC, sequence DESC LIMIT :limit)",
    )
    abstract suspend fun trim(limit: Int)

    @Transaction
    open suspend fun enqueue(entry: StoredLog) {
        insert(entry)
        trim(MAX_STORED_LOGS)
    }

    @Query("SELECT * FROM pending_logs ORDER BY sequence LIMIT 1")
    abstract fun oldest(): Flow<StoredLog?>

    @Query("DELETE FROM pending_logs WHERE eventId = :eventId")
    abstract suspend fun acknowledge(eventId: String)

    @Query("SELECT COUNT(*) FROM pending_logs")
    abstract suspend fun count(): Int
}

@Database(entities = [StoredLog::class], version = 1, exportSchema = true)
abstract class LogDatabase : RoomDatabase() {
    abstract fun logs(): LogDao
}
