package com.tvflow.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        LiveCategoryEntity::class,
        LiveChannelEntity::class,
        EpgProgramEntity::class,
        MediaCategoryEntity::class,
        VodItemEntity::class,
        SeriesItemEntity::class,
        PlaybackPositionEntity::class,
        WatchHistoryEntity::class
    ],
    version = 13,
    exportSchema = true
)
abstract class TVFlowDatabase : RoomDatabase() {
    abstract fun dao(): TVFlowDao
}
