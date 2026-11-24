package com.ambiback.manager.data.usb

import android.content.Context
import android.net.Uri
import com.ambiback.manager.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/**
 * Flashes an ESP32 device over USB serial using the ROM bootloader protocol.
 *
 * The user provides a .zip file containing:
 *   - bootloader_qio_80m.bin  (flashed at 0x1000)
 *   - partitions.bin          (flashed at 0x8000)
 *   - boot_app0.bin           (flashed at 0xE000)
 *   - firmware.bin            (flashed at 0x10000)
 *   - flash_config.json       (optional – overrides default offsets)
 *
 * flash_config.json format (array):
 *   [ {"file": "firmware.bin", "offset": 65536}, ... ]
 *
 * Protocol references:
 *   - SLIP framing     : RFC 1055
 *   - ESP32 ROM loader : https://docs.espressif.com/projects/esptool/en/latest/esp32/advanced-topics/serial-protocol.html
 */
class EspRomFlasher(private val port: UsbSerialPort) {

    // ── Public entry point ────────────────────────────────────────────────────

    suspend fun flash(
        context: Context,
        zipUri: Uri,
        onProgress: (progress: Float, status: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            onProgress(0f, context.getString(R.string.flash_entering_bootloader))
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            enterBootloader()
            drainReceiveBuffer()

            onProgress(0.05f, context.getString(R.string.flash_syncing))
            check(sync()) { context.getString(R.string.flash_err_sync) }

            onProgress(0.08f, context.getString(R.string.flash_attaching))
            check(sendCommand(CMD_SPI_ATTACH, ByteArray(8))) { "SPI_ATTACH failed" }

            onProgress(0.10f, context.getString(R.string.flash_baud))
            val baudCmd = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(921600).putInt(0).array()
            if (sendCommand(CMD_CHANGE_BAUDRATE, baudCmd)) {
                Thread.sleep(50)
                port.setParameters(921600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                Thread.sleep(50)
            }

            onProgress(0.12f, context.getString(R.string.flash_reading))
            val partitions = readZipPartitions(context, zipUri)
            val grandTotal = partitions.sumOf { it.data.size }.toLong().coerceAtLeast(1L)
            var flashedSoFar = 0L

            partitions.forEachIndexed { idx, partition ->
                val blockSize = FLASH_BLOCK_SIZE
                val numBlocks = (partition.data.size + blockSize - 1) / blockSize
                val eraseSize = numBlocks * blockSize
                val isLast    = idx == partitions.lastIndex

                onProgress(
                    (0.12f + 0.85f * (flashedSoFar.toFloat() / grandTotal)).coerceIn(0f, 0.97f),
                    context.getString(R.string.flash_erasing, partition.offset.toString(16).uppercase())
                )

                val beginData = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(eraseSize).putInt(numBlocks).putInt(blockSize).putInt(partition.offset)
                    .array()
                check(sendCommand(CMD_FLASH_BEGIN, beginData, timeoutMs = 30_000)) {
                    "FLASH_BEGIN failed for partition at 0x${partition.offset.toString(16)}"
                }

                for (seq in 0 until numBlocks) {
                    val start = seq * blockSize
                    val end   = minOf(start + blockSize, partition.data.size)
                    val block = ByteArray(blockSize) { 0xFF.toByte() }
                    partition.data.copyInto(block, destinationOffset = 0, startIndex = start, endIndex = end)

                    val cs = calcChecksum(block)
                    val dataHeader = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(block.size).putInt(seq).putInt(0).putInt(0).array()

                    check(sendCommand(CMD_FLASH_DATA, dataHeader + block, checksum = cs)) {
                        "FLASH_DATA failed at seq $seq / partition 0x${partition.offset.toString(16)}"
                    }

                    val progressBytes = flashedSoFar + (seq + 1).toLong() * blockSize
                    onProgress(
                        (0.12f + 0.85f * (progressBytes.toFloat() / grandTotal)).coerceIn(0f, 0.97f),
                        context.getString(R.string.flash_writing, idx + 1, partitions.size, seq + 1, numBlocks)
                    )
                }

                // Best-effort MD5 verification against the ROM loader.
                onProgress(
                    (0.12f + 0.85f * ((flashedSoFar + partition.data.size).toFloat() / grandTotal)).coerceIn(0f, 0.97f),
                    context.getString(R.string.flash_verifying, idx + 1, partitions.size)
                )
                verifyFlashMd5(partition.offset, partition.data)

                val endData = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(if (isLast) 0 else 1).array()
                sendCommand(CMD_FLASH_END, endData)

                flashedSoFar += partition.data.size
            }

            onProgress(0.98f, context.getString(R.string.flash_rebooting))
            onProgress(1f, context.getString(R.string.flash_complete))
        } finally {
            // Always leave the chip out of download mode, even on failure.
            try {
                hardReset()
                port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                Thread.sleep(200)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Ask the ROM loader for the MD5 of the just-written flash region and compare
     * it to the MD5 of [data]. Soft-fails (returns quietly) when the loader does not
     * support SPI_FLASH_MD5 or returns an unexpected payload.
     *
     * Payload layouts (after the 8-byte response header):
     * - Stub: 16 raw MD5 bytes + 2 status bytes
     * - ROM:  32 hex-coded ASCII MD5 bytes + status bytes (2 or 4)
     */
    private fun verifyFlashMd5(offset: Int, data: ByteArray) {
        val expected = java.security.MessageDigest.getInstance("MD5").digest(data)
        val md5Cmd = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(offset)
            .putInt(data.size)
            .putInt(0)
            .putInt(0)
            .array()
        val response = sendCommandRaw(CMD_SPI_FLASH_MD5, md5Cmd, timeoutMs = 10_000) ?: return
        if (response.size < 10 ||
            response[0] != 0x01.toByte() ||
            response[1] != CMD_SPI_FLASH_MD5
        ) return

        val size = (response[2].toInt() and 0xff) or ((response[3].toInt() and 0xff) shl 8)
        if (size < 18 || response.size < 8 + size) return
        val payload = response.copyOfRange(8, 8 + size)

        val actual: ByteArray = when {
            // Stub loader: 16 binary digest + 2 status
            payload.size >= 18 && payload.size < 32 -> {
                if (payload[16] != 0x00.toByte()) return
                payload.copyOfRange(0, 16)
            }
            // ROM loader: 32 ASCII hex digest + status bytes
            payload.size >= 34 -> {
                if (payload[32] != 0x00.toByte()) return
                val hex = payload.copyOfRange(0, 32).toString(Charsets.US_ASCII)
                hexStringToBytes(hex) ?: return
            }
            else -> return
        }

        check(actual.contentEquals(expected)) {
            "MD5 mismatch at offset 0x${offset.toString(16)}"
        }
    }

    /** Like [sendCommand] but returns the raw decoded response frame, or null on I/O failure. */
    private fun sendCommandRaw(
        cmd: Byte,
        data: ByteArray,
        checksum: Int = 0,
        timeoutMs: Int = 3000
    ): ByteArray? {
        val frame = slipEncode(buildCommandFrame(cmd, data, checksum))
        return try {
            port.write(frame, timeoutMs)
            readSlipFrame(timeoutMs)
        } catch (_: Exception) {
            null
        }
    }

    private fun hexStringToBytes(hex: String): ByteArray? {
        if (hex.length != 32 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            return null
        }
        return ByteArray(16) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    // ── Bootloader entry / exit ───────────────────────────────────────────────

    /**
     * Pull IO0 low via DTR, then pulse EN/RST via RTS to enter ROM download mode.
     * DTR → GPIO0 (active-low = hold low during reset to select download mode)
     * RTS → EN / RST (active-low = assert reset)
     */
    private fun enterBootloader() {
        port.setDTR(false); port.setRTS(false); Thread.sleep(100)
        port.setDTR(true)                                          // IO0 LOW
        port.setRTS(true);                       Thread.sleep(100) // EN  LOW (reset asserted)
        port.setRTS(false);                      Thread.sleep(50)  // EN  HIGH (release — boots into loader)
        port.setDTR(false);                      Thread.sleep(50)  // IO0 HIGH (release)
    }

    private fun hardReset() {
        port.setDTR(false)
        port.setRTS(true);  Thread.sleep(100)
        port.setRTS(false); Thread.sleep(100)
    }

    // ── SLIP framing ──────────────────────────────────────────────────────────

    private fun slipEncode(data: ByteArray): ByteArray = SlipFraming.encode(data)

    private fun slipDecode(raw: ByteArray): ByteArray = SlipFraming.decode(raw)

    // ── Low-level command frame builder ───────────────────────────────────────

    /**
     * Builds an ESP ROM command packet:
     * direction(1) | cmd(1) | size(2 LE) | checksum(4 LE) | data
     */
    private fun buildCommandFrame(cmd: Byte, data: ByteArray, checksum: Int): ByteArray {
        val size = data.size
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .put(0x00.toByte())    // direction: request
            .put(cmd)
            .putShort(size.toShort())
            .putInt(checksum)
            .array()
        return header + data
    }

    // ── Command send / response ───────────────────────────────────────────────

    /**
     * Reads bytes until a complete SLIP frame (0xC0 … 0xC0) is received,
     * then SLIP-decodes it and returns the decoded payload.
     * Returns null on timeout or error.
     */
    private fun readSlipFrame(timeoutMs: Int): ByteArray? {
        val buf   = ByteArray(512)
        val frame = ArrayList<Byte>(256)
        var started = false
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val n = try { port.read(buf, 100) } catch (_: Exception) { 0 }
            for (i in 0 until n) {
                val b = buf[i]
                if (!started) {
                    if (b == SlipFraming.END) { started = true; frame.add(b) }
                } else {
                    frame.add(b)
                    if (b == SlipFraming.END && frame.size > 1) {
                        return slipDecode(frame.toByteArray())
                    }
                }
            }
        }
        return null
    }

    /**
     * Sends [cmd] with [data] and waits for a matching success response.
     * ROM responses: [direction=0x01][cmd][size(2)][value(4)][error][status]
     * Success = response[0]==0x01 AND response[1]==cmd AND response[8]==0x00
     */
    private fun sendCommand(
        cmd: Byte,
        data: ByteArray,
        checksum: Int = 0,
        timeoutMs: Int = 3000
    ): Boolean {
        val frame = slipEncode(buildCommandFrame(cmd, data, checksum))
        return try {
            port.write(frame, timeoutMs)
            val response = readSlipFrame(timeoutMs) ?: return false
            response.size >= 10 &&
                    response[0] == 0x01.toByte() &&
                    response[1] == cmd &&
                    response[8] == 0x00.toByte()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Sends SYNC frames (up to 10) and waits for any valid SYNC response.
     * The ROM loader sends multiple response frames for a single SYNC.
     */
    private fun sync(): Boolean {
        val syncData = byteArrayOf(0x07, 0x07, 0x12, 0x20) + ByteArray(32) { 0x55.toByte() }
        val syncFrame = slipEncode(buildCommandFrame(CMD_SYNC, syncData, 0))

        repeat(10) { attempt ->
            try { port.write(syncFrame, 500) } catch (_: Exception) { }
            // Read frames greedily for 300 ms; any valid SYNC response is success
            val deadline = System.currentTimeMillis() + 300
            while (System.currentTimeMillis() < deadline) {
                val response = readSlipFrame(100) ?: continue
                if (response.size >= 3 &&
                    response[0] == 0x01.toByte() &&
                    response[1] == CMD_SYNC
                ) {
                    drainReceiveBuffer()   // discard remaining queued SYNC responses
                    return true
                }
            }
            Thread.sleep(50L * (attempt + 1))
        }
        return false
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** XOR checksum used for FLASH_DATA blocks (ESP32 ROM convention, seed = 0xEF). */
    private fun calcChecksum(data: ByteArray): Int {
        var cs = 0xEF
        for (b in data) cs = cs xor (b.toInt() and 0xFF)
        return cs
    }

    private fun drainReceiveBuffer() {
        try { port.read(ByteArray(4096), 300) } catch (_: Exception) {}
    }

    // ── Zip parsing ───────────────────────────────────────────────────────────

    private data class FlashPartition(val offset: Int, val data: ByteArray)
    private data class PartitionConfig(val file: String, val offset: Int)

    private val defaultConfigs = listOf(
        PartitionConfig("bootloader_qio_80m.bin", 0x1000),
        PartitionConfig("partitions.bin",          0x8000),
        PartitionConfig("boot_app0.bin",           0xE000),
        PartitionConfig("firmware.bin",            0x10000)
    )

    private fun readZipPartitions(context: Context, zipUri: Uri): List<FlashPartition> {
        val entries = mutableMapOf<String, ByteArray>()

        context.contentResolver.openInputStream(zipUri)?.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.substringAfterLast('/')
                    entries[name] = zip.readBytes()
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalArgumentException(context.getString(R.string.flash_err_open))

        val configs: List<PartitionConfig> = entries["flash_config.json"]?.let { jsonBytes ->
            try {
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                Gson().fromJson<List<Map<String, Any>>>(
                    String(jsonBytes, Charsets.UTF_8), type
                ).map { m ->
                    PartitionConfig(m["file"] as String, (m["offset"] as Double).toInt())
                }
            } catch (_: Exception) { defaultConfigs }
        } ?: defaultConfigs

        val result = configs.mapNotNull { cfg ->
            entries[cfg.file]?.let { FlashPartition(cfg.offset, it) }
        }
        check(result.isNotEmpty()) {
            context.getString(
                R.string.flash_err_no_partitions,
                "bootloader_qio_80m.bin, partitions.bin, boot_app0.bin, firmware.bin"
            )
        }
        return result
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val FLASH_BLOCK_SIZE     = 0x400   // 1024 bytes per write block

        private val CMD_SYNC            = 0x08.toByte()
        private val CMD_SPI_ATTACH      = 0x0D.toByte()
        private val CMD_CHANGE_BAUDRATE = 0x0F.toByte()
        private val CMD_FLASH_BEGIN     = 0x02.toByte()
        private val CMD_FLASH_DATA      = 0x03.toByte()
        private val CMD_FLASH_END       = 0x04.toByte()
        private val CMD_SPI_FLASH_MD5   = 0x13.toByte()
    }
}
