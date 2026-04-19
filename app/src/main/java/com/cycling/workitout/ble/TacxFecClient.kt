package com.cycling.workitout.ble

/**
 * Builds ANT+ FE-C frames for tunneling over Tacx's proprietary BLE service
 * (UUID `6e40fec1-…`).
 *
 * Context: Tacx smart trainers (Neo / Neo 2 / Neo 2T / Flux S / etc.) ship with
 * a user-selectable BLE mode in the Tacx Training app. In "FE-C BLE" mode they
 * expose this service instead of FTMS (0x1826) — so standard FTMS control
 * (opcode 0x05 Set Target Power) is unavailable. The trainer still accepts ERG
 * targets, but they must be delivered as ANT+ FE-C Page 49 messages wrapped in
 * the ANT serial framing and written to characteristic `6e40fec3`.
 *
 * Frame layout for acknowledged data pages (13 bytes total):
 *
 *   off  bytes  meaning
 *   ───  ─────  ──────────────────────────────────────────────────────
 *    0   A4     ANT sync
 *    1   09     length (msg id + channel + 8-byte page = 9 is convention;
 *               spec technically puts msg id outside, but every open-source
 *               Tacx client uses 0x09 here and trainers accept it)
 *    2   4F     message id = acknowledged data
 *    3   05     channel = FE-C (0x05 per ANT+ FE-C device profile)
 *    4   31     data page 49 (Target Power)
 *   5-9  FF×5   reserved (spec: 0xFF)
 *   10   pp     target power LSB  ┐ little-endian uint16 in 0.25 W units,
 *   11   pp     target power MSB  ┘ i.e. watts × 4
 *   12   cc     XOR checksum of bytes 0..11
 *
 * Reference implementations that agree on this framing:
 *   - GoldenCheetah `FecBike.cpp`
 *   - Wahoo's ANT+ FE-C sample code
 *   - OpenANT + various Tacx reverse-engineering write-ups
 */
object TacxFecClient {

    /**
     * Build an ANT+ FE-C Page 49 (Target Power) frame.
     * [watts] is clamped to a sane trainer range (0..4000 W).
     */
    fun buildTargetPowerFrame(watts: Int): ByteArray {
        val quarterWatts = (watts.coerceIn(0, 4000) * 4).coerceAtMost(0xFFFF)
        val lsb = (quarterWatts and 0xFF).toByte()
        val msb = ((quarterWatts shr 8) and 0xFF).toByte()

        val frame = ByteArray(13)
        frame[0]  = BleConstants.ANT_SYNC
        frame[1]  = 0x09
        frame[2]  = BleConstants.ANT_MSG_ACKNOWLEDGED_DATA
        frame[3]  = BleConstants.ANT_FEC_CHANNEL
        frame[4]  = BleConstants.ANT_PAGE_TARGET_POWER
        frame[5]  = 0xFF.toByte()
        frame[6]  = 0xFF.toByte()
        frame[7]  = 0xFF.toByte()
        frame[8]  = 0xFF.toByte()
        frame[9]  = 0xFF.toByte()
        frame[10] = lsb
        frame[11] = msb
        frame[12] = xorChecksum(frame, 0, 12)
        return frame
    }

    /** XOR checksum over [from, until). */
    private fun xorChecksum(bytes: ByteArray, from: Int, until: Int): Byte {
        var c = 0
        for (i in from until until) c = c xor (bytes[i].toInt() and 0xFF)
        return (c and 0xFF).toByte()
    }
}
