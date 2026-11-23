package com.ambiback.manager.data.network

import com.ambiback.manager.data.model.DeviceConfig
import com.ambiback.manager.data.model.DeviceStatus
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service interface for the AmbiBack LED controller HTTP API.
 *
 * The base URL is set dynamically per device using [RetrofitFactory].
 * All endpoints are plain HTTP (port 80) on the device IP.
 */
interface DeviceApiService {

    /** Returns the full device configuration as JSON. */
    @GET("getparamatersasjson")
    suspend fun getConfig(): DeviceConfig

    /** Returns live runtime status (FPS, WiFi, memory). */
    @GET("getstatusasjson")
    suspend fun getStatus(): DeviceStatus

    /**
     * Switch LED mode.
     * @param mode use constants from [com.ambiback.manager.data.model.LedMode]
     */
    @GET("setcolormode")
    suspend fun setColorMode(@Query("mode") mode: Int)

    /**
     * Set a static ambient color.
     * @param color 8-character hex string in WWRRGGBB format (e.g. "00FF0000" for red)
     */
    @GET("setcolor")
    suspend fun setColor(@Query("color") color: String)

    /**
     * Set a single device parameter.
     * Common params: brightness, minimumluminosity, colorboost, ambientspeed, ambientintensity,
     * colorred, colorgreen, colorblue, framelength, framesmoothing
     */
    @GET("setparam")
    suspend fun setParam(@Query("param") param: String, @Query("value") value: Int)
}
