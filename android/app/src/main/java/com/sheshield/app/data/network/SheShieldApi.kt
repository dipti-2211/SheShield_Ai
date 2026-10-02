package com.sheshield.app.data.network

import com.google.gson.JsonObject
import com.sheshield.app.data.model.*
import retrofit2.http.*

interface SheShieldApi {
    @GET("ready") suspend fun ready(): ReadyResponse
    @POST("v1/sessions") suspend fun enroll(@Body body: Map<String,String>): SessionResponse
    @GET("v1/places") suspend fun places(@Query("q") query: String): PlacesResponse
    @POST("v1/plans") suspend fun plan(@Body body: JsonObject): TripPlan
    @POST("v1/trips") suspend fun start(@Header("Idempotency-Key") key: String, @Body body: JsonObject): RemoteTrip
    @GET("v1/trips/{id}") suspend fun trip(@Path("id") id: String): RemoteTrip
    @POST("v1/trips/{id}/locations") suspend fun location(@Path("id") id: String, @Body fix: LocationFix): RemoteTrip
    @POST suspend fun command(@Url path: String,@Header("Idempotency-Key") key: String,@Body body: JsonObject): JsonObject
    @POST("v1/sos") suspend fun sos(@Header("Idempotency-Key") key: String,@Body body: JsonObject): SosIncident
    @GET("v1/sos/{id}") suspend fun incident(@Path("id") id: String): SosIncident
    @POST("v1/trips/{id}/reroute") suspend fun reroute(@Path("id") id: String,@Body body: JsonObject): RerouteProposal
    @POST("v1/trips/{id}/share") suspend fun share(@Path("id") id: String, @Body body: JsonObject = JsonObject()): ShareResponse
    @DELETE("v1/trips/{id}/share") suspend fun revokeShares(@Path("id") id: String): JsonObject
    @DELETE("v1/trips/{id}") suspend fun deleteTrip(@Path("id") id: String): JsonObject
}
