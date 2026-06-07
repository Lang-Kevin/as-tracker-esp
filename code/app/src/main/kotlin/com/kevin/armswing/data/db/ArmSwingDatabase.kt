package com.kevin.armswing.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kevin.armswing.data.entity.Session
import com.kevin.armswing.data.entity.VelocitySample

@Database(entities = [Session::class, VelocitySample::class], version = 2, exportSchema = false)
abstract class ArmSwingDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun velocitySampleDao(): VelocitySampleDao
}
