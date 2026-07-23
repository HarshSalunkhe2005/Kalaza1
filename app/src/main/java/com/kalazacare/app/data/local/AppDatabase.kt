package com.kalazacare.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PatientEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
}
