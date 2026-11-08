package com.miniweb.browser.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history")
data class HistoryEntry(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String, val url: String, val visitedAt: Long = System.currentTimeMillis())

@Entity(tableName = "bookmarks", indices = [Index(value = ["url"], unique = true)])
data class Bookmark(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String, val url: String, val createdAt: Long = System.currentTimeMillis())

@Dao interface BrowserDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 500") fun observeHistory(): Flow<List<HistoryEntry>>
    @Insert suspend fun addHistory(entry: HistoryEntry)
    @Query("DELETE FROM history") suspend fun clearHistory()
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC") fun observeBookmarks(): Flow<List<Bookmark>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addBookmark(bookmark: Bookmark)
    @Query("DELETE FROM bookmarks WHERE url = :url") suspend fun removeBookmark(url: String)
}

@Database(entities = [HistoryEntry::class, Bookmark::class], version = 1, exportSchema = true)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun browserDao(): BrowserDao
    companion object {
        @Volatile private var INSTANCE: BrowserDatabase? = null
        fun get(context: Context): BrowserDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, BrowserDatabase::class.java, "mini_web.db").build().also { INSTANCE = it }
        }
    }
}
