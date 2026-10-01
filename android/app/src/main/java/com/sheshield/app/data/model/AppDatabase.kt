package com.sheshield.app.data.model

import android.content.Context
import androidx.room.*

@Database(entities = [ActiveTrip::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(ctx: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                ctx.applicationContext,
                AppDatabase::class.java,
                "sheshield.db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
