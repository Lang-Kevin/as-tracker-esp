package com.kevin.armswing.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kevin.armswing.data.entity.OmegaSample
import com.kevin.armswing.data.entity.Session

@Database(entities = [Session::class, OmegaSample::class], version = 1, exportSchema = false)
abstract class ArmSwingDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun omegaSampleDao(): OmegaSampleDao
}
