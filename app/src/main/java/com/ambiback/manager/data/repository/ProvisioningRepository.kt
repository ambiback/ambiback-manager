package com.ambiback.manager.data.repository

import com.ambiback.manager.data.model.ProvisioningClaimResponse
import com.ambiback.manager.data.model.ProvisioningFinishResponse
import com.ambiback.manager.data.model.ProvisioningStatus
import com.ambiback.manager.data.model.ProvisioningSubmitResponse
import com.ambiback.manager.data.model.ProvisioningWpsResponse
import com.ambiback.manager.data.network.createProvisioningService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for all WiFi provisioning API calls.
 *
 * Each method creates a fresh [ProvisioningApiService] for the given gateway IP
 * and returns a [Result], so the ViewModel never needs to catch exceptions.
 *
 * The gateway IP should be resolved via [GatewayResolver] after the phone
 * associates with the controller SoftAP, not hard-coded.
 *
 * SECURITY: credentials are never stored — they exist only in the ViewModel
 * UiState for the duration of the active provisioning session and are cleared
 * on success or cancellation.
 */
@Singleton
class ProvisioningRepository @Inject constructor() {

    /**
     * Read the current provisioning state from the controller.
     * Use this to verify controller presence and whether a session is active.
     */
    suspend fun getProvisioningStatus(gatewayIp: String): Result<ProvisioningStatus> =
        runCatching { createProvisioningService(gatewayIp).getProvisioningStatus() }

    /**
     * Start (or restart) a provisioning session.
     * Returns a fresh token in the response on success.
     */
    suspend fun startSession(gatewayIp: String, claimKey: String? = null): Result<ProvisioningStatus> =
        runCatching { createProvisioningService(gatewayIp).startSession(claimKey) }

    /** Pair this app with the device by storing [newKey] on it. */
    suspend fun claimDevice(
        gatewayIp: String,
        token:     String,
        newKey:    String,
        claimKey:  String? = null
    ): Result<ProvisioningClaimResponse> =
        runCatching { createProvisioningService(gatewayIp).claimDevice(token, newKey, claimKey) }

    /**
     * Trigger WPS mode on the connected controller.
     */
    suspend fun startWps(gatewayIp: String, claimKey: String? = null): Result<ProvisioningWpsResponse> =
        runCatching { createProvisioningService(gatewayIp).startWps(claimKey) }

    /**
     * Stage WiFi credentials on the controller.
     *
     * @param token    Session token from [startSession].
     * @param ssid     Target WiFi SSID (required, non-empty).
     * @param password Target WiFi password; empty string for open networks.
     */
    suspend fun submitCredentials(
        gatewayIp: String,
        token:     String,
        ssid:      String,
        password:  String = "",
        claimKey:  String? = null
    ): Result<ProvisioningSubmitResponse> =
        runCatching {
            createProvisioningService(gatewayIp)
                .submitCredentials(token, ssid, password, claimKey)
        }

    /**
     * Finalize provisioning: controller tries to join the staged network.
     *
     * Success  → firmware saves credentials, state becomes idle.
     * Failure  → firmware keeps state=pending; call [submitCredentials] with
     *            corrected credentials and then [finishProvisioning] again.
     */
    suspend fun finishProvisioning(
        gatewayIp: String,
        token:     String,
        claimKey:  String? = null
    ): Result<ProvisioningFinishResponse> =
        runCatching {
            createProvisioningService(gatewayIp).finishProvisioning(token, claimKey)
        }
}
