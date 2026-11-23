package com.ambiback.manager.data.network

import com.ambiback.manager.data.model.ProvisioningFinishResponse
import com.ambiback.manager.data.model.ProvisioningStatus
import com.ambiback.manager.data.model.ProvisioningSubmitResponse
import com.ambiback.manager.data.model.ProvisioningWpsResponse
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Retrofit service interface for the AmbiBack controller provisioning API.
 *
 * This interface targets the controller SoftAP (resolved via [GatewayResolver]).
 * All calls are plain HTTP; the base URL is set per request via [createProvisioningService].
 *
 * IMPORTANT: The firmware reads POST bodies as form parameters, NOT JSON.
 * All POST endpoints use [FormUrlEncoded] / [Field] annotations accordingly.
 */
interface ProvisioningApiService {

    /**
     * Read current provisioning state.
     * Returns [ProvisioningStatus] regardless of whether a session is active.
     */
    @GET("getprovisioningasjson")
    suspend fun getProvisioningStatus(): ProvisioningStatus

    /**
     * Start (or restart) a provisioning session.
     * Firmware creates a fresh 16-char hex token and clears any pending staged credentials.
     */
    @GET("provisioning/start")
    suspend fun startSession(): ProvisioningStatus

    /**
     * Trigger controller-side WPS mode.
     * Firmware requires an explicit confirmation query parameter.
     */
    @GET("startwps?forreal=1")
    suspend fun startWps(): ProvisioningWpsResponse

    /**
     * Submit WiFi credentials to stage them on the controller.
     *
     * @param token    Session token received from [startSession].
     * @param ssid     Target infrastructure WiFi SSID (required, non-empty).
     * @param password Target WiFi password; may be empty for open networks.
     */
    @FormUrlEncoded
    @POST("provisioning/submit")
    suspend fun submitCredentials(
        @Field("token")    token:    String,
        @Field("ssid")     ssid:     String,
        @Field("password") password: String
    ): ProvisioningSubmitResponse

    /**
     * Finalize provisioning: firmware attempts to join the staged network.
     * On success: firmware saves credentials and enters idle state.
     * On failure: firmware keeps state=pending so the app can retry.
     *
     * @param token Session token received from [startSession].
     */
    @FormUrlEncoded
    @POST("provisioning/finish")
    suspend fun finishProvisioning(
        @Field("token") token: String
    ): ProvisioningFinishResponse
}

// ---------------------------------------------------------------------------
// Factory
// ---------------------------------------------------------------------------

private val provisioningClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .build()

/**
 * Creates a [ProvisioningApiService] for the given gateway IP.
 * Default IP is the standard ESP32 SoftAP address; prefer calling
 * [GatewayResolver.resolve] to get the actual gateway after AP association.
 */
fun createProvisioningService(ip: String = "192.168.4.1"): ProvisioningApiService =
    Retrofit.Builder()
        .baseUrl("http://$ip/")
        .client(provisioningClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ProvisioningApiService::class.java)
