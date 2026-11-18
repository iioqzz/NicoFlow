package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloaded_videos ORDER BY downloadedAt DESC")
    fun observeAllDownloads(): Flow<List<DownloadedVideoEntity>>

    @Query("SELECT * FROM downloaded_videos WHERE contentId = :contentId LIMIT 1")
    suspend fun getDownload(contentId: String): DownloadedVideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadedVideoEntity)

    @Query("UPDATE downloaded_videos SET progress = :progress, status = :status, fileSizeBytes = :sizeBytes, localFilePath = :localPath WHERE contentId = :contentId")
    suspend fun updateProgress(
        contentId: String,
        progress: Float,
        status: String,
        sizeBytes: Long,
        localPath: String
    )

    @Query("DELETE FROM downloaded_videos WHERE contentId = :contentId")
    suspend fun deleteById(contentId: String)
}

@Dao
interface CommentDao {
    @Query("SELECT * FROM cached_comments WHERE videoId = :videoId ORDER BY vposMs ASC")
    fun observeComments(videoId: String): Flow<List<CachedCommentEntity>>

    @Query("SELECT * FROM cached_comments WHERE videoId = :videoId ORDER BY vposMs ASC")
    suspend fun getComments(videoId: String): List<CachedCommentEntity>

    @Query("SELECT COUNT(*) FROM cached_comments WHERE videoId = :videoId")
    suspend fun countComments(videoId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(comments: List<CachedCommentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(comment: CachedCommentEntity)

    @Query("DELETE FROM cached_comments WHERE videoId = :videoId AND isUserPosted = 0")
    suspend fun clearRemoteComments(videoId: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC LIMIT 100")
    fun observeHistory(): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchHistoryEntity)

    @Query("UPDATE watch_history SET lastPositionMs = :positionMs, watchedAt = :watchedAt WHERE contentId = :contentId")
    suspend fun updatePosition(contentId: String, positionMs: Long, watchedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}

@Dao
interface MylistDao {
    @Query("SELECT * FROM mylist_videos ORDER BY addedAt DESC")
    fun observeMylist(): Flow<List<MylistVideoEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM mylist_videos WHERE contentId = :contentId)")
    fun observeIsInMylist(contentId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MylistVideoEntity)

    @Query("DELETE FROM mylist_videos WHERE contentId = :contentId")
    suspend fun deleteById(contentId: String)
}
