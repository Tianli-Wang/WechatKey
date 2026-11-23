package com.tianl.key.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.tianl.key.model.ConnectionStatus
import com.tianl.key.model.DiscoveredDevice
import com.tianl.key.model.VehicleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "TianlKey_Ble"
        private const val DEFAULT_MTU = 23
        private const val TARGET_MTU = 512
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    private var currentMtu = DEFAULT_MTU

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _vehicleState = MutableStateFlow(VehicleState())
    val vehicleState: StateFlow<VehicleState> = _vehicleState.asStateFlow()

    private val _rawMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val rawMessages: SharedFlow<String> = _rawMessages.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val receiveBuffer = StringBuilder()

    // Write queue for multi-packet or sequential writes
    private val writeQueue = ConcurrentLinkedQueue<ByteArray>()
    private var isWriting = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val scanRecord = result.scanRecord
            val rawName = scanRecord?.deviceName ?: device.name ?: ""
            if (rawName.startsWith(BleConfig.DEVICE_NAME_PREFIX)) {
                addDiscoveredDevice(device.address, rawName, result.rssi)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (result in results) {
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            _errors.tryEmit("扫描失败，错误代码: $errorCode")
        }
    }

    private fun addDiscoveredDevice(address: String, name: String, rssi: Int) {
        val currentList = _discoveredDevices.value.toMutableList()
        val index = currentList.indexOfFirst { it.address == address }
        val item = DiscoveredDevice(address, name, rssi)
        if (index >= 0) {
            currentList[index] = item
        } else {
            currentList.add(item)
        }
        currentList.sortByDescending { it.rssi }
        _discoveredDevices.value = currentList
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun startScan(): Boolean {
        if (!isBluetoothEnabled()) {
            _errors.tryEmit("蓝牙未开启")
            return false
        }
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            _errors.tryEmit("无法获取低功耗蓝牙扫描器")
            return false
        }

        _discoveredDevices.value = emptyList()
        _isScanning.value = true

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleConfig.SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            // Also start without strict filter to catch devices where Service UUID is in Scan Response
            scanner.startScan(null, settings, scanCallback)
            return true
        } catch (e: Exception) {
            _isScanning.value = false
            _errors.tryEmit("启动扫描异常: ${e.message}")
            return false
        }
    }

    fun stopScan() {
        if (!_isScanning.value) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Stop scan error", e)
        } finally {
            _isScanning.value = false
        }
    }

    fun connect(macAddress: String) {
        stopScan()
        disconnect()

        val adapter = bluetoothAdapter ?: run {
            _errors.tryEmit("蓝牙不可用")
            return
        }

        val device = try {
            adapter.getRemoteDevice(macAddress)
        } catch (e: Exception) {
            _errors.tryEmit("无效的蓝牙地址: $macAddress")
            return
        }

        _connectionStatus.value = ConnectionStatus.CONNECTING
        _connectedDevice.value = device

        scope.launch {
            try {
                bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(
                        context,
                        false,
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE
                    )
                } else {
                    device.connectGatt(context, false, gattCallback)
                }
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                _errors.tryEmit("建立连接异常: ${e.message}")
            }
        }
    }

    fun disconnect() {
        try {
            bluetoothGatt?.let { gatt ->
                gatt.disconnect()
                gatt.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing gatt", e)
        } finally {
            bluetoothGatt = null
            writeCharacteristic = null
            notifyCharacteristic = null
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            _connectedDevice.value = null
            currentMtu = DEFAULT_MTU
            synchronized(receiveBuffer) { receiveBuffer.setLength(0) }
            writeQueue.clear()
            isWriting = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status=$status, newState=$newState")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Connection failed with status: $status")
                disconnect()
                _errors.tryEmit("连接失败 (状态码: $status)")
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionStatus.value = ConnectionStatus.CONNECTING
                scope.launch {
                    delay(350) // Delay to ensure Android and ESP32 BLE stack stability
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        gatt.requestMtu(TARGET_MTU)
                    } else {
                        gatt.discoverServices()
                    }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnect()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "onMtuChanged mtu=$mtu, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentMtu = mtu
            }
            scope.launch {
                delay(200)
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnect()
                _errors.tryEmit("服务发现失败 (错误码 $status)")
                return
            }

            val service = gatt.getService(BleConfig.SERVICE_UUID)
            if (service == null) {
                disconnect()
                _errors.tryEmit("未发现钥匙服务 ${BleConfig.SERVICE_UUID}，请确认固件匹配")
                return
            }

            // Write characteristic
            var writeChar = service.getCharacteristic(BleConfig.WRITE_CHAR_UUID)
            if (writeChar == null) {
                writeChar = service.characteristics.firstOrNull {
                    (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) ||
                            (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
                }
            }

            // Notify characteristic
            var notifyChar = service.getCharacteristic(BleConfig.NOTIFY_CHAR_UUID)
            if (notifyChar == null) {
                notifyChar = service.characteristics.firstOrNull {
                    (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) ||
                            (it.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
                }
            }

            if (writeChar == null || notifyChar == null) {
                disconnect()
                _errors.tryEmit("ESP32 钥匙服务特征不完整 (缺少可写或可通知特征)")
                return
            }

            writeCharacteristic = writeChar
            notifyCharacteristic = notifyChar

            // Enable notification
            enableNotification(gatt, notifyChar)
        }

        private fun enableNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val enabled = gatt.setCharacteristicNotification(characteristic, true)
            if (!enabled) {
                _errors.tryEmit("开启通知失败")
                return
            }

            val descriptor = characteristic.getDescriptor(BleConfig.CCCD_UUID)
            if (descriptor != null) {
                val value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, value)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = value
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
            } else {
                // If no CCCD descriptor found, directly complete connection setup
                completeConnection(gatt)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "onDescriptorWrite status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                completeConnection(gatt)
            } else {
                _errors.tryEmit("写入通知描述符失败 (错误码 $status)")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: return
            handleIncomingBytes(bytes)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingBytes(value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            synchronized(writeQueue) {
                isWriting = false
                processWriteQueue()
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _vehicleState.value = _vehicleState.value.copy(rssi = rssi)
            }
        }
    }

    private fun completeConnection(gatt: BluetoothGatt) {
        _connectionStatus.value = ConnectionStatus.CONNECTED
        // Query status immediately
        scope.launch {
            delay(200)
            readRssi()
        }
    }

    fun readRssi() {
        bluetoothGatt?.readRemoteRssi()
    }

    private fun handleIncomingBytes(bytes: ByteArray) {
        val chunk = String(bytes, StandardCharsets.UTF_8)
        synchronized(receiveBuffer) {
            receiveBuffer.append(chunk)
            var newlineIndex = receiveBuffer.indexOf("\n")
            while (newlineIndex != -1) {
                val line = receiveBuffer.substring(0, newlineIndex).trim()
                receiveBuffer.delete(0, newlineIndex + 1)
                if (line.isNotEmpty()) {
                    processReceivedJson(line)
                }
                newlineIndex = receiveBuffer.indexOf("\n")
            }
        }
    }

    private fun processReceivedJson(jsonStr: String) {
        Log.d(TAG, "Received JSON: $jsonStr")
        _rawMessages.tryEmit(jsonStr)

        try {
            val json = JSONObject(jsonStr)
            val currentState = _vehicleState.value

            val locked = if (json.has("locked")) json.getBoolean("locked") else currentState.locked
            val powered = if (json.has("powered")) json.getBoolean("powered") else currentState.powered
            val battery = if (json.has("battery")) json.getInt("battery") else currentState.battery
            val rssi = if (json.has("rssi")) json.getInt("rssi") else currentState.rssi
            val name = if (json.has("name")) json.getString("name") else currentState.name
            val message = if (json.has("message")) json.getString("message") else currentState.message
            val ok = if (json.has("ok")) json.getBoolean("ok") else null
            val action = if (json.has("action")) json.getString("action") else null

            _vehicleState.value = VehicleState(
                locked = locked,
                powered = powered,
                battery = battery,
                rssi = rssi,
                name = name,
                message = message,
                ok = ok,
                action = action
            )
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse fallback for message: $jsonStr", e)
            _vehicleState.value = _vehicleState.value.copy(message = jsonStr)
        }
    }

    fun send(command: String, secret: String, extraData: Map<String, Any>? = null): Boolean {
        if (_connectionStatus.value != ConnectionStatus.CONNECTED || writeCharacteristic == null) {
            _errors.tryEmit("蓝牙未连接或写入特征未就绪")
            return false
        }

        val json = JSONObject()
        extraData?.forEach { (k, v) -> json.put(k, v) }
        json.put("cmd", command)
        json.put("key", secret)
        json.put("ts", System.currentTimeMillis() / 1000)

        val payload = (json.toString() + "\n").toByteArray(StandardCharsets.UTF_8)
        enqueueWrite(payload)
        return true
    }

    private fun enqueueWrite(data: ByteArray) {
        val maxChunk = (currentMtu - 3).coerceIn(20, 512)
        var offset = 0
        while (offset < data.size) {
            val end = (offset + maxChunk).coerceAtMost(data.size)
            val chunk = data.copyOfRange(offset, end)
            writeQueue.add(chunk)
            offset = end
        }
        synchronized(writeQueue) {
            if (!isWriting) {
                processWriteQueue()
            }
        }
    }

    private fun processWriteQueue() {
        val gatt = bluetoothGatt ?: return
        val char = writeCharacteristic ?: return
        val chunk = writeQueue.poll() ?: return

        isWriting = true
        val writeType = if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = gatt.writeCharacteristic(char, chunk, writeType)
            if (status != 0 /* BluetoothStatusCodes.SUCCESS */) {
                isWriting = false
                _errors.tryEmit("写入特征异常，错误码: $status")
            }
        } else {
            @Suppress("DEPRECATION")
            char.value = chunk
            char.writeType = writeType
            @Suppress("DEPRECATION")
            val success = gatt.writeCharacteristic(char)
            if (!success) {
                isWriting = false
                _errors.tryEmit("写入特征失败")
            }
        }
    }
}
