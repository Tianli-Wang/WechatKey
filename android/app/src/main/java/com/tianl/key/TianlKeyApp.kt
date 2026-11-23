package com.tianl.key

import android.app.Application
import com.tianl.key.ble.BleManager
import com.tianl.key.data.VehiclePreferences

class TianlKeyApp : Application() {
    lateinit var bleManager: BleManager
        private set

    lateinit var vehiclePreferences: VehiclePreferences
        private set

    override fun onCreate() {
        super.onCreate()
        bleManager = BleManager(this)
        vehiclePreferences = VehiclePreferences(this)
    }
}
