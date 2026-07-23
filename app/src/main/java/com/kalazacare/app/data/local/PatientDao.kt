package com.kalazacare.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PatientDao {
    @Query("SELECT * FROM cached_patients")
    suspend fun getAll(): List<PatientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(patients: List<PatientEntity>)

    @Query("DELETE FROM cached_patients")
    suspend fun clear()
}
