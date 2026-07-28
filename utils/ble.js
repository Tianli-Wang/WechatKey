/**
 * ESP32 BLE 协议配置。
 * 这些 UUID 必须与 esp32Code/main/main.c 中的定义保持一致。
 */
const BLE_CONFIG = {
  serviceId: '6E400001-B5A3-F393-E0A9-E50E24DCCA9E',
  writeCharacteristicId: '6E400002-B5A3-F393-E0A9-E50E24DCCA9E',
  notifyCharacteristicId: '6E400003-B5A3-F393-E0A9-E50E24DCCA9E',
  deviceNamePrefix: 'TianlKey'
}

function stringToBuffer(text) {
  const bytes = []
  for (let i = 0; i < text.length; i += 1) {
    const code = text.charCodeAt(i)
    if (code < 0x80) {
      bytes.push(code)
    } else if (code < 0x800) {
      bytes.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f))
    } else {
      bytes.push(
        0xe0 | (code >> 12),
        0x80 | ((code >> 6) & 0x3f),
        0x80 | (code & 0x3f)
      )
    }
  }
  return new Uint8Array(bytes).buffer
}

function bufferToString(buffer) {
  const bytes = new Uint8Array(buffer)
  let result = ''
  for (let i = 0; i < bytes.length; i += 1) {
    result += String.fromCharCode(bytes[i])
  }
  try {
    return decodeURIComponent(escape(result))
  } catch (error) {
    return result
  }
}

function normalizeUuid(uuid) {
  return (uuid || '').toUpperCase()
}

/**
 * 等待指定时间。iOS 建立 BLE 连接后不会立刻完成 GATT 服务发现，
 * 因此连接成功和查询服务之间需要留出少量时间。
 */
function delay(milliseconds) {
  return new Promise((resolve) => {
    setTimeout(resolve, milliseconds)
  })
}

/**
 * 为底层蓝牙错误补充操作名称和错误码，避免界面只显示一段被截断的英文。
 */
function createBleError(action, error) {
  const code = error && typeof error.errCode !== 'undefined'
    ? `（错误码 ${error.errCode}）`
    : ''
  const detail = error && errMsgWithoutPrefix(error.errMsg)
  const result = new Error(`${action}失败${code}${detail ? `：${detail}` : ''}`)
  result.errCode = error && error.errCode
  result.originalError = error
  return result
}

function errMsgWithoutPrefix(message) {
  if (!message) return ''
  const separator = message.indexOf(':')
  return separator >= 0 ? message.slice(separator + 1).trim() : message
}

class BleKey {
  constructor() {
    this.deviceId = ''
    this.serviceId = ''
    this.writeCharacteristicId = ''
    this.notifyCharacteristicId = ''
    this.writeType = 'write'
    this.notifyHandler = null
    this.connectionHandler = null
    this.deviceFoundHandler = null
    this.connectionStateListener = null
    this.characteristicValueListener = null
  }

  openAdapter() {
    return new Promise((resolve, reject) => {
      wx.openBluetoothAdapter({ success: resolve, fail: reject })
    })
  }

  startDiscovery(onDevice) {
    if (this.deviceFoundHandler && wx.offBluetoothDeviceFound) {
      wx.offBluetoothDeviceFound(this.deviceFoundHandler)
    }
    this.deviceFoundHandler = (result) => {
      result.devices.forEach((device) => {
        const name = device.name || device.localName || ''
        if (name.indexOf(BLE_CONFIG.deviceNamePrefix) === 0) {
          onDevice({ ...device, name })
        }
      })
    }
    wx.onBluetoothDeviceFound(this.deviceFoundHandler)
    return new Promise((resolve, reject) => {
      wx.startBluetoothDevicesDiscovery({
        services: [BLE_CONFIG.serviceId],
        allowDuplicatesKey: true,
        interval: 700,
        powerLevel: 'medium',
        success: resolve,
        fail: reject
      })
    })
  }

  stopDiscovery() {
    return new Promise((resolve) => {
      wx.stopBluetoothDevicesDiscovery({ complete: resolve })
    })
  }

  connect(deviceId) {
    return new Promise((resolve, reject) => {
      wx.createBLEConnection({
        deviceId,
        timeout: 10000,
        success: async () => {
          this.deviceId = deviceId
          this.setupConnectionListener()
          try {
            // iOS 连接回调触发时，系统通常仍在后台发现 GATT 服务。
            await delay(350)
            await this.discoverCharacteristics()
            await this.enableNotify()
            resolve()
          } catch (error) {
            reject(error)
          }
        },
        fail: (error) => reject(createBleError('建立蓝牙连接', error))
      })
    })
  }

  setupConnectionListener() {
    // 重连前移除旧监听，避免一次断开事件被重复处理。
    if (this.connectionStateListener && wx.offBLEConnectionStateChange) {
      wx.offBLEConnectionStateChange(this.connectionStateListener)
    }
    this.connectionStateListener = (state) => {
      if (state.deviceId === this.deviceId && this.connectionHandler) {
        this.connectionHandler(state.connected)
      }
    }
    wx.onBLEConnectionStateChange(this.connectionStateListener)
  }

  onConnectionChange(handler) {
    this.connectionHandler = handler
  }

  /**
   * 先读取 ESP32 实际暴露的服务和特征，再保存微信返回的真实 UUID。
   * 这一步同时验证 RX 是否可写、TX 是否支持 notify/indicate。
   */
  async discoverCharacteristics() {
    let services = []

    // 个别 iPhone 首次查询会返回空服务列表，最多重试三次。
    for (let attempt = 0; attempt < 3; attempt += 1) {
      services = await this.getServices()
      if (services.length) break
      await delay(300)
    }

    const service = services.find((item) => {
      return normalizeUuid(item.uuid) === normalizeUuid(BLE_CONFIG.serviceId)
    })
    if (!service) {
      throw new Error(
        `未发现钥匙服务 ${BLE_CONFIG.serviceId}，请确认 ESP32 已烧录匹配固件`
      )
    }

    let characteristics = []
    for (let attempt = 0; attempt < 3; attempt += 1) {
      characteristics = await this.getCharacteristics(service.uuid)
      if (characteristics.length) break
      await delay(300)
    }

    const expectedWrite = characteristics.find((item) => {
      return normalizeUuid(item.uuid) ===
        normalizeUuid(BLE_CONFIG.writeCharacteristicId)
    })
    const expectedNotify = characteristics.find((item) => {
      return normalizeUuid(item.uuid) ===
        normalizeUuid(BLE_CONFIG.notifyCharacteristicId)
    })

    // 优先使用约定 UUID；若固件修改过 UUID，则在同一服务内按属性兼容选择。
    const writeCharacteristic = (
      expectedWrite &&
      (expectedWrite.properties.write || expectedWrite.properties.writeNoResponse)
    ) ? expectedWrite : characteristics.find((item) => {
      return item.properties.write || item.properties.writeNoResponse
    })
    const notifyCharacteristic = (
      expectedNotify &&
      (expectedNotify.properties.notify || expectedNotify.properties.indicate)
    ) ? expectedNotify : characteristics.find((item) => {
      return item.properties.notify || item.properties.indicate
    })

    if (!writeCharacteristic) {
      throw new Error('ESP32 钥匙服务中没有可写入的特征')
    }
    if (!notifyCharacteristic) {
      throw new Error('ESP32 钥匙服务中没有支持通知或指示的特征')
    }

    this.serviceId = service.uuid
    this.writeCharacteristicId = writeCharacteristic.uuid
    this.notifyCharacteristicId = notifyCharacteristic.uuid
    this.writeType = writeCharacteristic.properties.write
      ? 'write'
      : 'writeNoResponse'
  }

  getServices() {
    return new Promise((resolve, reject) => {
      wx.getBLEDeviceServices({
        deviceId: this.deviceId,
        success: (result) => resolve(result.services || []),
        fail: (error) => reject(createBleError('读取蓝牙服务', error))
      })
    })
  }

  getCharacteristics(serviceId) {
    return new Promise((resolve, reject) => {
      wx.getBLEDeviceCharacteristics({
        deviceId: this.deviceId,
        serviceId,
        success: (result) => resolve(result.characteristics || []),
        fail: (error) => reject(createBleError('读取蓝牙特征', error))
      })
    })
  }

  /**
   * 开启 ESP32 状态通知。iOS 偶尔会在 GATT 刚发现后暂时拒绝订阅，
   * 这里以短间隔重试，仍失败时保留底层错误码。
   */
  async enableNotify() {
    if (this.characteristicValueListener && wx.offBLECharacteristicValueChange) {
      wx.offBLECharacteristicValueChange(this.characteristicValueListener)
    }
    this.characteristicValueListener = (result) => {
      if (
        this.notifyHandler &&
        normalizeUuid(result.characteristicId) ===
          normalizeUuid(this.notifyCharacteristicId)
      ) {
        // ESP32 每次通知返回一条完整 JSON，解析失败时保留原始文本。
        const raw = bufferToString(result.value).trim()
        try {
          this.notifyHandler(JSON.parse(raw))
        } catch (error) {
          this.notifyHandler({ message: raw })
        }
      }
    }
    wx.onBLECharacteristicValueChange(this.characteristicValueListener)

    let lastError = null
    for (let attempt = 0; attempt < 3; attempt += 1) {
      if (attempt > 0) await delay(350)
      try {
        await this.setNotifyState(true)
        return
      } catch (error) {
        lastError = error
      }
    }
    throw createBleError('开启车辆状态通知', lastError)
  }

  setNotifyState(state) {
    return new Promise((resolve, reject) => {
      wx.notifyBLECharacteristicValueChange({
        deviceId: this.deviceId,
        serviceId: this.serviceId,
        characteristicId: this.notifyCharacteristicId,
        state,
        type: 'notification',
        success: resolve,
        fail: reject
      })
    })
  }

  onMessage(handler) {
    this.notifyHandler = handler
  }

  send(command, secret, extraData) {
    if (
      !this.deviceId ||
      !this.serviceId ||
      !this.writeCharacteristicId
    ) {
      return Promise.reject(new Error('蓝牙未连接或特征尚未就绪'))
    }
    // 附加字段用于重命名等扩展指令，核心鉴权字段始终由小程序覆盖。
    const commandData = Object.assign({}, extraData || {}, {
      cmd: command,
      key: secret,
      ts: Math.floor(Date.now() / 1000)
    })
    const payload = JSON.stringify(commandData) + '\n'

    return new Promise((resolve, reject) => {
      wx.writeBLECharacteristicValue({
        deviceId: this.deviceId,
        serviceId: this.serviceId,
        characteristicId: this.writeCharacteristicId,
        value: stringToBuffer(payload),
        writeType: this.writeType,
        success: resolve,
        fail: (error) => reject(createBleError('发送车辆指令', error))
      })
    })
  }

  disconnect() {
    if (!this.deviceId) return Promise.resolve()
    const deviceId = this.deviceId
    this.deviceId = ''
    this.serviceId = ''
    this.writeCharacteristicId = ''
    this.notifyCharacteristicId = ''
    return new Promise((resolve) => {
      wx.closeBLEConnection({ deviceId, complete: resolve })
    })
  }

  close() {
    return this.disconnect().then(() => new Promise((resolve) => {
      if (this.deviceFoundHandler && wx.offBluetoothDeviceFound) {
        wx.offBluetoothDeviceFound(this.deviceFoundHandler)
      }
      if (this.connectionStateListener && wx.offBLEConnectionStateChange) {
        wx.offBLEConnectionStateChange(this.connectionStateListener)
      }
      if (
        this.characteristicValueListener &&
        wx.offBLECharacteristicValueChange
      ) {
        wx.offBLECharacteristicValueChange(this.characteristicValueListener)
      }
      wx.closeBluetoothAdapter({ complete: resolve })
    }))
  }
}

module.exports = {
  BleKey,
  BLE_CONFIG,
  normalizeUuid
}
