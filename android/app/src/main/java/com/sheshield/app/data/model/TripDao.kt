package com.sheshield.app.data.model

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface TripDao {

    @Query("SELECT * FROM active_trip WHERE state NOT IN ('COMPLETED', 'CANCELLED') LIMIT 1")
    fun observeActiveTrip(): LiveData<ActiveTrip?>

    @Query("SELECT * FROM active_trip WHERE state NOT IN ('COMPLETED', 'CANCELLED') LIMIT 1")
    suspend fun getActiveTrip(): ActiveTrip?

    @Query("SELECT * FROM active_trip WHERE tripId = :id LIMIT 1")
    suspend fun getTripById(id: String): ActiveTrip?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(trip: ActiveTrip)

    @Query("UPDATE active_trip SET state = :state WHERE tripId = :id")
    suspend fun updateState(id: String, state: TripState)

    @Query("UPDATE active_trip SET lastLatitude = :lat, lastLongitude = :lng, lastUpdateMs = :ts WHERE tripId = :id")
    suspend fun updateLocation(id: String, lat: Double, lng: Double, ts: Long)

    @Query("UPDATE active_trip SET state = :state, checkInDeadlineMs = :deadline WHERE tripId = :id")
    suspend fun setCheckInState(id: String, state: TripState, deadline: Long)

    @Query("DELETE FROM active_trip WHERE tripId = :id")
    suspend fun delete(id: String)
}
