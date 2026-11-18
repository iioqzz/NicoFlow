package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DownloadedVideoEntity::class,
        CachedCommentEntity::class,
        WatchHistoryEntity::class,
        MylistVideoEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NicoDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun commentDao(): CommentDao
    abstract fun historyDao(): HistoryDao
    abstract fun mylistDao(): MylistDao

    companion object {
        @Volatile
        private var INSTANCE: NicoDatabase? = null

        fun getInstance(context: Context): NicoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NicoDatabase::class.java,
                    "nicoflow.db"
                ).fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
