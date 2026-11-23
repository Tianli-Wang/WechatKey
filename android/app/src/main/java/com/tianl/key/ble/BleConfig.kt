package com.tianl.key.ble

import java.util.UUID

object BleConfig {
    val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    val WRITE_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    val NOTIFY_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val DEVICE_NAME_PREFIX = "TianlKey"
    const val DEVICE_PREFIX_FULL = "TianlKey-"
    const val NAME_SUFFIX_MAX_BYTES = 12

    fun extractSuffix(name: String?): String {
        return if (name != null && name.startsWith(DEVICE_PREFIX_FULL)) {
            name.substring(DEVICE_PREFIX_FULL.length)
        } else {
            ""
        }
    }
}
