package com.kevin.armswing.di

import android.content.Context
import androidx.room.Room
import com.kevin.armswing.data.db.ArmSwingDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ArmSwingDatabase =
        Room.databaseBuilder(context, ArmSwingDatabase::class.java, "arm_swing.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSessionDao(db: ArmSwingDatabase) = db.sessionDao()

    @Provides
    fun provideVelocitySampleDao(db: ArmSwingDatabase) = db.velocitySampleDao()
}
