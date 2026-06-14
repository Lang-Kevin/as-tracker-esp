package com.kevin.armswing.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kevin.armswing.data.entity.Session
import com.kevin.armswing.data.entity.VelocitySample

@Database(entities = [Session::class, VelocitySample::class], version = 3, exportSchema = false)
abstract class ArmSwingDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun velocitySampleDao(): VelocitySampleDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Session ADD COLUMN deletedAt INTEGER")
            }
        }
    }
}
