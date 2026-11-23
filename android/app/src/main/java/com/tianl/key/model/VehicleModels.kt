package com.tianl.key.model

data class DiscoveredDevice(
    val address: String,
    val name: String,
    val rssi: Int
) {
    val signalText: String get() = signalLabel(rssi)
}

data class VehicleState(
    val locked: Boolean = true,
    val powered: Boolean = false,
    val battery: Int = 78,
    val rssi: Int = -100,
    val name: String = "尚未连接车辆",
    val message: String = "等待连接",
    val ok: Boolean? = null,
    val action: String? = null
) {
    val signalText: String get() = signalLabel(rssi)
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

fun signalLabel(rssi: Int): String {
    return when {
        rssi >= -60 -> "很强"
        rssi >= -72 -> "良好"
        rssi >= -85 -> "较弱"
        else -> "微弱"
    }
}
