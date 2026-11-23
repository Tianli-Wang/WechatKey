package com.tianl.key.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tianl.key.ble.BleConfig
import com.tianl.key.ble.BleManager
import com.tianl.key.data.VehiclePreferences
import com.tianl.key.model.ConnectionStatus
import com.tianl.key.model.DiscoveredDevice
import com.tianl.key.model.VehicleState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

data class UiAlert(
    val title: String,
    val message: String
)

class VehicleViewModel(
    private val bleManager: BleManager,
    private val preferences: VehiclePreferences
) : ViewModel() {

    val connectionStatus: StateFlow<ConnectionStatus> = bleManager.connectionStatus
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = bleManager.discoveredDevices

    private val _vehicleState = MutableStateFlow(VehicleState())
    val vehicleState: StateFlow<VehicleState> = _vehicleState.asStateFlow()

    private val _currentAddress = MutableStateFlow<String?>(null)
    val currentAddress: StateFlow<String?> = _currentAddress.asStateFlow()

    private val _currentName = MutableStateFlow("尚未连接车辆")
    val currentName: StateFlow<String> = _currentName.asStateFlow()

    private val _secret = MutableStateFlow("")
    val secret: StateFlow<String> = _secret.asStateFlow()

    private val _vehicleType = MutableStateFlow("large")
    val vehicleType: StateFlow<String> = _vehicleType.asStateFlow()

    private val _busyCommand = MutableStateFlow<String?>(null)
    val busyCommand: StateFlow<String?> = _busyCommand.asStateFlow()

    // Dialog & overlay controls
    private val _showScanDialog = MutableStateFlow(false)
    val showScanDialog: StateFlow<Boolean> = _showScanDialog.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    private val _showPowerConfirmDialog = MutableStateFlow(false)
    val showPowerConfirmDialog: StateFlow<Boolean> = _showPowerConfirmDialog.asStateFlow()

    private val _uiToast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiToast: SharedFlow<String> = _uiToast.asSharedFlow()

    private val _uiAlert = MutableSharedFlow<UiAlert>(extraBufferCapacity = 4)
    val uiAlert: SharedFlow<UiAlert> = _uiAlert.asSharedFlow()

    private var pendingRenameSuffix: String? = null
    private var renameTimeoutJob: Job? = null

    init {
        // Load local preferences
        _secret.value = preferences.getSecret()
        val saved = preferences.getSavedDevice()
        if (saved != null) {
            _currentAddress.value = saved.first
            _currentName.value = saved.second
            _vehicleType.value = preferences.getVehicleType(saved.first)
            _vehicleState.value = _vehicleState.value.copy(name = saved.second)
        }

        // Collect ble vehicle state updates
        viewModelScope.launch {
            bleManager.vehicleState.collect { state ->
                val current = _vehicleState.value
                val updatedName = if (state.name.startsWith(BleConfig.DEVICE_NAME_PREFIX)) {
                    state.name
                } else {
                    current.name
                }

                _vehicleState.value = state.copy(name = updatedName)
                if (state.name.startsWith(BleConfig.DEVICE_NAME_PREFIX)) {
                    _currentName.value = state.name
                    _currentAddress.value?.let { addr ->
                        preferences.saveDevice(addr, state.name)
                    }
                }
                handleRenameResponse(state)
            }
        }

        // Collect ble errors
        viewModelScope.launch {
            bleManager.errors.collect { errorMsg ->
                _uiToast.tryEmit(errorMsg)
            }
        }

        // Collect connection status changes
        viewModelScope.launch {
            bleManager.connectionStatus.collect { status ->
                when (status) {
                    ConnectionStatus.DISCONNECTED -> {
                        _vehicleState.value = _vehicleState.value.copy(
                            powered = false,
                            message = "车辆已离线"
                        )
                    }
                    ConnectionStatus.CONNECTED -> {
                        _vehicleState.value = _vehicleState.value.copy(
                            message = "钥匙已连接"
                        )
                        _uiToast.tryEmit("蓝牙已连接")
                        delay(200)
                        sendCommand("status", quiet = true)
                    }
                    ConnectionStatus.CONNECTING -> {
                        _vehicleState.value = _vehicleState.value.copy(
                            message = "正在建立连接…"
                        )
                    }
                }
            }
        }
    }

    fun isBluetoothReady(): Boolean {
        return bleManager.isBluetoothEnabled()
    }

    fun openScanDialog() {
        if (connectionStatus.value == ConnectionStatus.CONNECTING) return
        _showScanDialog.value = true
        startScan()
    }

    fun closeScanDialog() {
        stopScan()
        _showScanDialog.value = false
    }

    fun startScan() {
        bleManager.startScan()
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun selectDevice(device: DiscoveredDevice) {
        closeScanDialog()
        _currentAddress.value = device.address
        _currentName.value = device.name
        _vehicleType.value = preferences.getVehicleType(device.address)
        preferences.saveDevice(device.address, device.name)
        connectDevice(device.address)
    }

    fun handleBluetoothConnectionClick() {
        when (connectionStatus.value) {
            ConnectionStatus.CONNECTED -> {
                disconnect()
                _uiToast.tryEmit("蓝牙已断开")
            }
            ConnectionStatus.CONNECTING -> {
                _uiToast.tryEmit("正在连接蓝牙…")
            }
            ConnectionStatus.DISCONNECTED -> {
                val saved = preferences.getSavedDevice()
                if (saved != null) {
                    _uiToast.tryEmit("正在连接车辆…")
                    connectDevice(saved.first)
                } else {
                    openScanDialog()
                }
            }
        }
    }

    fun connectDevice(address: String) {
        bleManager.connect(address)
    }

    fun disconnect() {
        bleManager.disconnect()
        _vehicleState.value = _vehicleState.value.copy(
            powered = false,
            message = "蓝牙连接已断开"
        )
    }

    fun togglePower() {
        if (connectionStatus.value != ConnectionStatus.CONNECTED) {
            val saved = preferences.getSavedDevice()
            if (saved != null) {
                connectDevice(saved.first)
            } else {
                openScanDialog()
            }
            return
        }

        if (_secret.value.isEmpty()) {
            _showSettingsDialog.value = true
            _uiToast.tryEmit("请先设置配对密钥")
            return
        }

        if (!_vehicleState.value.powered) {
            _showPowerConfirmDialog.value = true
        } else {
            sendCommand("power_off")
        }
    }

    fun confirmPowerOn() {
        _showPowerConfirmDialog.value = false
        sendCommand("power_on")
    }

    fun dismissPowerConfirm() {
        _showPowerConfirmDialog.value = false
    }

    fun findVehicle() {
        if (!ensureConnected()) return
        sendCommand("find")
    }

    fun refreshStatus() {
        if (!ensureConnected()) return
        sendCommand("status", quiet = true)
        bleManager.readRssi()
    }

    fun sendCommand(command: String, quiet: Boolean = false) {
        if (connectionStatus.value != ConnectionStatus.CONNECTED) {
            _uiToast.tryEmit("请先连接车辆")
            return
        }

        if (command != "status" && _secret.value.isEmpty()) {
            _showSettingsDialog.value = true
            _uiToast.tryEmit("请先设置配对密钥")
            return
        }

        _busyCommand.value = command
        val success = bleManager.send(command, _secret.value)
        _busyCommand.value = null

        if (success) {
            // Optimistic updates
            val current = _vehicleState.value
            val optimistic = when (command) {
                "lock" -> current.copy(locked = true, powered = false, message = "车辆已上锁")
                "unlock" -> current.copy(locked = false, message = "车辆已解锁")
                "power_on" -> current.copy(powered = true, locked = false, message = "车辆已启动")
                "power_off" -> current.copy(powered = false, message = "车辆已关闭")
                "find" -> current.copy(message = "车辆正在鸣笛闪灯")
                else -> current
            }
            _vehicleState.value = optimistic
            if (!quiet && optimistic.message.isNotEmpty()) {
                _uiToast.tryEmit(optimistic.message)
            }
        }
    }

    fun openSettingsDialog() {
        _showSettingsDialog.value = true
    }

    fun closeSettingsDialog() {
        _showSettingsDialog.value = false
    }

    fun openRenameSettings() {
        val saved = preferences.getSavedDevice()
        if (saved == null) {
            _uiToast.tryEmit("请先添加车辆")
            return
        }
        if (connectionStatus.value != ConnectionStatus.CONNECTED) {
            _uiToast.tryEmit("请先连接蓝牙再重命名")
            return
        }
        openSettingsDialog()
    }

    fun saveSecret(newSecret: String) {
        if (newSecret.length < 6) {
            _uiToast.tryEmit("密钥至少需要 6 位")
            return
        }
        _secret.value = newSecret
        preferences.saveSecret(newSecret)
        _uiToast.tryEmit("配对密钥已保存")
    }

    fun setVehicleType(type: String) {
        if (type != "large" && type != "compact") return
        val addr = _currentAddress.value ?: preferences.getSavedDevice()?.first
        if (addr.isNullOrEmpty()) {
            _uiToast.tryEmit("请先添加车辆")
            return
        }
        _vehicleType.value = type
        preferences.saveVehicleType(addr, type)
        _uiToast.tryEmit(if (type == "compact") "已选择小车" else "已选择大车")
    }

    fun renameVehicle(suffix: String) {
        if (!ensureConnected()) return
        if (_secret.value.isEmpty()) {
            _uiToast.tryEmit("请先设置配对密钥")
            return
        }

        val trimmed = suffix.trim()
        val byteLen = trimmed.toByteArray(StandardCharsets.UTF_8).size
        if (trimmed.isEmpty() || byteLen > BleConfig.NAME_SUFFIX_MAX_BYTES) {
            _uiToast.tryEmit("后缀需为 1–12 个字节")
            return
        }

        if (trimmed.contains("\"") || trimmed.contains("\\") || trimmed.contains("\r") || trimmed.contains("\n")) {
            _uiToast.tryEmit("名称不能包含引号或反斜杠")
            return
        }

        _busyCommand.value = "rename"
        startRenameWait(trimmed)

        val success = bleManager.send("rename", _secret.value, mapOf("name" to trimmed))
        if (!success) {
            cancelRenameWait()
            _busyCommand.value = null
        }
    }

    private fun startRenameWait(suffix: String) {
        cancelRenameWait()
        pendingRenameSuffix = suffix
        renameTimeoutJob = viewModelScope.launch {
            delay(5000)
            pendingRenameSuffix = null
            _busyCommand.value = null
            _uiAlert.tryEmit(
                UiAlert(
                    title = "车辆没有回应",
                    message = "请确认 ESP32 已烧录支持重命名的新固件，并检查蓝牙连接后重试。"
                )
            )
        }
    }

    private fun cancelRenameWait() {
        renameTimeoutJob?.cancel()
        renameTimeoutJob = null
        pendingRenameSuffix = null
    }

    private fun handleRenameResponse(state: VehicleState) {
        val pending = pendingRenameSuffix ?: return
        if (state.ok == null) return

        if (state.action != "rename" && state.ok == true) return

        val expectedName = BleConfig.DEVICE_PREFIX_FULL + pending
        cancelRenameWait()
        _busyCommand.value = null

        if (state.ok == true && state.name == expectedName) {
            _uiToast.tryEmit("车辆名称已保存")
        } else {
            val reason = state.message.ifEmpty { "ESP32 未接受新的车辆名称" }
            val current = if (state.name.isNotEmpty()) "\n当前名称：${state.name}" else ""
            _uiAlert.tryEmit(
                UiAlert(
                    title = "重命名失败",
                    message = "$reason$current\n请确认配对密钥与固件一致。"
                )
            )
        }
    }

    fun forgetVehicle() {
        disconnect()
        val addr = _currentAddress.value ?: preferences.getSavedDevice()?.first
        preferences.clearDevice()
        if (addr != null) {
            preferences.clearVehicleType(addr)
        }
        _currentAddress.value = null
        _currentName.value = "尚未连接车辆"
        _vehicleType.value = "large"
        _showSettingsDialog.value = false
        _vehicleState.value = VehicleState(
            name = "尚未连接车辆",
            message = "等待连接"
        )
        _uiToast.tryEmit("已移除车辆")
    }

    private fun ensureConnected(): Boolean {
        if (connectionStatus.value == ConnectionStatus.CONNECTED) return true
        _uiToast.tryEmit("请先连接车辆")
        return false
    }

    class Factory(
        private val bleManager: BleManager,
        private val preferences: VehiclePreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VehicleViewModel(bleManager, preferences) as T
        }
    }
}
