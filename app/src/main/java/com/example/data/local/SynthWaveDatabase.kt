package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        FavoriteEntity::class,
        RecentPlayEntity::class,
        CustomLyricsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SynthWaveDatabase : RoomDatabase() {
    abstract fun synthWaveDao(): SynthWaveDao

    companion object {
        @Volatile
        private var INSTANCE: SynthWaveDatabase? = null

        fun getInstance(context: Context): SynthWaveDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SynthWaveDatabase::class.java,
                    "synthwave_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
