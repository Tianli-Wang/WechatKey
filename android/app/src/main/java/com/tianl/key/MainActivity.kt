package com.tianl.key

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.tianl.key.ui.MainScreen
import com.tianl.key.ui.theme.TianlKeyTheme
import com.tianl.key.viewmodel.VehicleViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: VehicleViewModel by viewModels {
        val app = application as TianlKeyApp
        VehicleViewModel.Factory(app.bleManager, app.vehiclePreferences)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkAndAutoConnect()
        } else {
            Toast.makeText(this, "需要蓝牙权限才能搜索和连接车辆钥匙", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TianlKeyTheme {
                MainScreen(viewModel = viewModel)
            }
        }

        checkPermissionsAndConnect()
    }

    private fun checkPermissionsAndConnect() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            checkAndAutoConnect()
        }
    }

    private fun checkAndAutoConnect() {
        val app = application as TianlKeyApp
        val saved = app.vehiclePreferences.getSavedDevice()
        if (saved != null && viewModel.isBluetoothReady()) {
            viewModel.connectDevice(saved.first)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            val app = application as TianlKeyApp
            app.bleManager.disconnect()
        }
    }
}
