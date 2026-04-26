package com.cycling.workitout.ble

// Builds ANT+ FE-C frames for Tacx trainers running in "FE-C BLE" mode instead of FTMS.
// Target power goes in Page 49, encoded as quarter-watts (watts × 4), with XOR checksum.
object TacxFecClient {

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

    private fun xorChecksum(bytes: ByteArray, from: Int, until: Int): Byte {
        var c = 0
        for (i in from until until) c = c xor (bytes[i].toInt() and 0xFF)
        return (c and 0xFF).toByte()
    }
}
