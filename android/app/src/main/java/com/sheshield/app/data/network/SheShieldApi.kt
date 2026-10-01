package com.sheshield.app.data.network

import com.sheshield.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface for the SheShield n8n backend.
 * All webhook paths match the n8n workflow webhook nodes.
 */
interface SheShieldApi {

    /** n8n health check webhook */
    @GET("webhook/health")
    suspend fun health(): Response<HealthResponse>

    /** Plan a trip - returns route alternatives with risk scores */
    @POST("webhook/plan-trip")
    suspend fun planTrip(@Body request: PlanTripRequest): Response<PlanTripResponse>

    /** Start an active trip after the user selects a route */
    @POST("webhook/start-trip")
    suspend fun startTrip(@Body request: StartTripRequest): Response<StartTripResponse>

    /** Periodic location update during active trip */
    @POST("webhook/location-update")
    suspend fun locationUpdate(@Body request: LocationUpdateRequest): Response<LocationUpdateResponse>

    /** User confirms safety ("I'm Safe") or triggers SOS */
    @POST("webhook/check-in")
    suspend fun checkIn(@Body request: CheckInRequest): Response<CheckInResponse>

    /** Manual SOS trigger */
    @POST("webhook/sos")
    suspend fun triggerSos(@Body request: SosRequest): Response<SosResponse>

    /** User ends trip normally */
    @POST("webhook/end-trip")
    suspend fun endTrip(@Body request: EndTripRequest): Response<Unit>
}
