package com.rehabgrip.core.ble

/**
 * Decodes Crane Scale WH-C06 ("IF_B7") manufacturer data. See docs/BLE.md.
 *
 * Input is the payload AFTER the 2 company-ID bytes (what Android's
 * getManufacturerSpecificData(0x0100) returns). iOS callers strip them first.
 * - bytes 10..11: weight in hundredths, big-endian
 * - byte 14 (optional): high nibble = stable flag, low nibble = unit (1 kg, 0/2 lb, 3 st, 4 jin)
 */
object CraneAdvertisementParser {
    const val COMPANY_ID = 0x0100
    private const val WEIGHT_OFFSET = 10
    private const val FLAGS_OFFSET = 14
    private const val MIN_LENGTH = WEIGHT_OFFSET + 2
    private const val MAX_KG = 300.0

    fun parse(payload: ByteArray): ScaleReading? {
        if (payload.size < MIN_LENGTH) return null
        val raw = (payload[WEIGHT_OFFSET].toInt() and 0xFF shl 8) or (payload[WEIGHT_OFFSET + 1].toInt() and 0xFF)
        val flags = payload.getOrNull(FLAGS_OFFSET)?.toInt()?.and(0xFF)
        val factor = when (flags?.and(0x0F)) {
            0, 2 -> LB_TO_KG
            3 -> ST_TO_KG
            4 -> JIN_TO_KG
            else -> 1.0
        }
        val kg = raw / 100.0 * factor
        if (kg > MAX_KG) return null
        val stable = flags != null && (flags shr 4) != 0
        return ScaleReading(kg = kg, stable = stable)
    }

    /**
     * Accepts both formats: with the 2 company-ID bytes in front (iOS style, 0x0100
     * little-endian = 00 01) and the bare payload (Android style).
     */
    fun parseAny(data: ByteArray): ScaleReading? {
        if (data.size >= 2 && data[0].toInt() == 0x00 && data[1].toInt() == 0x01) {
            parse(data.copyOfRange(2, data.size))?.let { return it }
        }
        return parse(data)
    }

    /** Payload without the company-ID bytes, whatever the platform delivered. */
    fun payloadOf(data: ByteArray): ByteArray =
        if (data.size >= 2 && data[0].toInt() == 0x00 && data[1].toInt() == 0x01) data.copyOfRange(2, data.size) else data

    /** The 02 03 signature (18-byte frames) that the WH-C06 uses, as hang-ten does. */
    fun hasSignature(payload: ByteArray): Boolean =
        payload.size >= 17 && payload[0].toInt() == 0x02 && payload[1].toInt() == 0x03

    fun looksLikeScale(name: String?, payload: ByteArray?): Boolean =
        (name != null && listOf("WH-C06", "Crane", "IF_B7").any { name.contains(it, ignoreCase = true) }) ||
            (payload != null && hasSignature(payload))

    /** Builds a frame like the scale's (tests and the simulator). */
    fun encode(kg: Double, withCompanyId: Boolean = false): ByteArray {
        val raw = (kg * 100).toInt().coerceIn(0, 0xFFFF)
        val p = ByteArray(18)
        p[0] = 0x02; p[1] = 0x03
        p[10] = (raw shr 8).toByte(); p[11] = (raw and 0xFF).toByte()
        p[14] = 0x01
        return if (withCompanyId) byteArrayOf(0x00, 0x01) + p else p
    }

    /** Splits iOS-style manufacturer data (company ID little-endian in the first 2 bytes). */
    fun stripCompanyId(fullManufacturerData: ByteArray): ByteArray? {
        if (fullManufacturerData.size < 2) return null
        val id = (fullManufacturerData[0].toInt() and 0xFF) or (fullManufacturerData[1].toInt() and 0xFF shl 8)
        return if (id == COMPANY_ID) fullManufacturerData.copyOfRange(2, fullManufacturerData.size) else null
    }

    private const val LB_TO_KG = 0.45359237
    private const val ST_TO_KG = 6.35029318
    private const val JIN_TO_KG = 0.5
}
