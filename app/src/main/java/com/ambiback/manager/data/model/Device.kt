package com.ambiback.manager.data.model

import com.ambiback.manager.utils.DeviceKeys
import com.google.gson.annotations.SerializedName

/**
 * Device as announced via UDP discovery broadcast (port 1337).
 * Fields map directly to the JSON the ESP32 controller broadcasts.
 */
data class Device(
    @SerializedName("DeviceID") val deviceId: String,
    @SerializedName("DeviceMAC") val deviceMac: String,
    @SerializedName("DeviceName") val deviceName: String,
    @SerializedName("DeviceIP") val deviceIp: String,
    @SerializedName("DeviceType") val deviceType: String,
    @SerializedName("DeviceVersion") val deviceVersion: String,
    @SerializedName("LedStatus") val ledStatus: Int = 0
) {
    /** Unique app-side identity. DeviceID alone can collide between controllers. */
    val deviceKey: String get() = DeviceKeys.of(deviceMac, deviceId)
}

/**
 * Full device configuration returned by GET /getparamatersasjson.
 * Only the fields used by the app are mapped; additional controller
 * parameters are silently ignored by Gson.
 */
data class DeviceConfig(
    @SerializedName("deviceid") val deviceId: String = "",
    @SerializedName("devicename") val deviceName: String = "",
    @SerializedName("deviceip") val deviceIp: String = "",
    @SerializedName("devicetype") val deviceType: String = "",
    @SerializedName("deviceversion") val deviceVersion: String = "",
    @SerializedName("devicemac") val deviceMac: String = "",
    // LED layout
    @SerializedName("vertical") val vertical: Int = 22,
    @SerializedName("horizontal") val horizontal: Int = 40,
    // Color calibration (0-255)
    @SerializedName("colorred") val colorRed: Int = 255,
    @SerializedName("colorgreen") val colorGreen: Int = 255,
    @SerializedName("colorblue") val colorBlue: Int = 255,
    @SerializedName("brightness") val brightness: Int = 255,
    @SerializedName("minimumluminosity") val minimumLuminosity: Int = 0,
    @SerializedName("colorboost") val colorBoost: Int = 50,
    // Animation
    @SerializedName("ambientpreset") val ambientPreset: Int = 0,
    @SerializedName("ambientspeed") val ambientSpeed: Int = 5,
    @SerializedName("ambientintensity") val ambientIntensity: Int = 50,
    @SerializedName("ambienthealthintensity") val ambientHealthIntensity: Int = 80,
    // Frame timing
    @SerializedName("framelength") val frameLength: Int = 60,
    @SerializedName("framesmoothing") val frameSmoothing: Int = 4
)

/**
 * Live runtime status returned by GET /getstatusasjson.
 */
data class DeviceStatus(
    @SerializedName("devicename") val deviceName: String = "",
    @SerializedName("deviceip") val deviceIp: String = "",
    @SerializedName("ledmode") val ledMode: Int = 0,
    @SerializedName("ledstatus") val ledStatus: Int = 0,
    @SerializedName("wifiname") val wifiName: String = "",
    @SerializedName("wifisignal") val wifiSignal: Int = 0,
    @SerializedName("fpsledout") val fpsLedOut: Int = 0,
    @SerializedName("fpsnetin") val fpsNetIn: Int = 0,
    @SerializedName("freeheap") val freeHeap: Int = 0
)

/** Named LED mode values used by /setcolormode?mode=<value> */
object LedMode {
    const val OFF = 0
    const val AMBIENT = 2   // confirmed via web UI (AmbientPage calls setColorMode(2))
    const val VIDEO = 4
    const val CAM = 5
}

// ---------------------------------------------------------------------------
// Provisioning models  (controller SoftAP provisioning endpoints)
// ---------------------------------------------------------------------------

/**
 * Response from GET /getprovisioningasjson  AND  GET /provisioning/start.
 * All numeric/boolean fields are strings in the firmware JSON ("0"/"1").
 * [success] is only present on the /provisioning/start response.
 */
data class ProvisioningStatus(
    @SerializedName("active")     val active:     String = "0",
    @SerializedName("pending")    val pending:    String = "0",
    @SerializedName("state")      val state:      String = "idle",
    @SerializedName("started")    val started:    String = "0",
    @SerializedName("expiresin")  val expiresIn:  String = "0",
    @SerializedName("token")      val token:      String = "",
    @SerializedName("apenabled")  val apEnabled:  String = "0",
    @SerializedName("apsecurity") val apSecurity: String = "open",
    @SerializedName("apssid")     val apSsid:     String = "",
    @SerializedName("apip")       val apIp:       String = "192.168.4.1",
    @SerializedName("reason")     val reason:     String = "",
    /** Present on firmware with device claiming. */
    @SerializedName("claimed")    val claimed:    String = "",
    @SerializedName("claimable")  val claimable:  String = "",
    /** Only present in /provisioning/start response */
    @SerializedName("success")    val success:    String = ""
)

/** Response from POST /provisioning/submit (form-encoded). */
data class ProvisioningSubmitResponse(
    @SerializedName("success")   val success:   String = "0",
    @SerializedName("message")   val message:   String = "",
    @SerializedName("state")     val state:     String = "idle",
    @SerializedName("started")   val started:   String = "0",
    @SerializedName("expiresin") val expiresIn: String = "0",
    @SerializedName("token")     val token:     String = ""
)

/** Response from POST /provisioning/finish (form-encoded). */
data class ProvisioningFinishResponse(
    @SerializedName("success")   val success:   String = "0",
    @SerializedName("message")   val message:   String = "",
    @SerializedName("state")     val state:     String = "idle",
    @SerializedName("expiresin") val expiresIn: String = "0",
    @SerializedName("connected") val connected: String = "0",
    @SerializedName("rebootin")  val rebootIn:  String = "0"
)

/** Response from POST /provisioning/claim (form-encoded). */
data class ProvisioningClaimResponse(
    @SerializedName("success") val success: String = "0",
    @SerializedName("claimed") val claimed: String = "0",
    @SerializedName("message") val message: String = ""
)

/** Response from GET /startwps?forreal=1. */
data class ProvisioningWpsResponse(
    @SerializedName("status") val status: Int = 0,
    @SerializedName("result") val result: String = ""
)
