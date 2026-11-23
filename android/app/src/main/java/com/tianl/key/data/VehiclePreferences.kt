package com.tianl.key.data

import android.content.Context
import android.content.SharedPreferences

class VehiclePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("tianlkey_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SAVED_ADDRESS = "tianlkey_device_address"
        private const val KEY_SAVED_NAME = "tianlkey_device_name"
        private const val KEY_SECRET = "tianlkey_secret"
        private const val PREFIX_VEHICLE_TYPE = "tianlkey_type_"
    }

    fun getSavedDevice(): Pair<String, String>? {
        val address = prefs.getString(KEY_SAVED_ADDRESS, null) ?: return null
        val name = prefs.getString(KEY_SAVED_NAME, "TianlKey") ?: "TianlKey"
        return Pair(address, name)
    }

    fun saveDevice(address: String, name: String) {
        prefs.edit()
            .putString(KEY_SAVED_ADDRESS, address)
            .putString(KEY_SAVED_NAME, name)
            .apply()
    }

    fun clearDevice() {
        prefs.edit()
            .remove(KEY_SAVED_ADDRESS)
            .remove(KEY_SAVED_NAME)
            .apply()
    }

    fun getSecret(): String {
        return prefs.getString(KEY_SECRET, "") ?: ""
    }

    fun saveSecret(secret: String) {
        prefs.edit().putString(KEY_SECRET, secret).apply()
    }

    fun getVehicleType(address: String?): String {
        if (address.isNullOrEmpty()) return "large"
        return prefs.getString(PREFIX_VEHICLE_TYPE + address, "large") ?: "large"
    }

    fun saveVehicleType(address: String?, type: String) {
        if (address.isNullOrEmpty()) return
        prefs.edit().putString(PREFIX_VEHICLE_TYPE + address, type).apply()
    }

    fun clearVehicleType(address: String?) {
        if (address.isNullOrEmpty()) return
        prefs.edit().remove(PREFIX_VEHICLE_TYPE + address).apply()
    }
}
