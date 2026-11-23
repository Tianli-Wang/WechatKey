const { BleKey } = require('../../utils/ble')

const bleKey = new BleKey()
const STORAGE_DEVICE = 'tianlkey_device'
const STORAGE_SECRET = 'tianlkey_secret'
const STORAGE_VEHICLE_TYPES = 'tianlkey_vehicle_types'
const DEVICE_NAME_PREFIX = 'TianlKey-'
const NAME_SUFFIX_MAX_BYTES = 12

// 计算 UTF-8 字节数，确保完整蓝牙名称可以放入传统扫描响应包。
function utf8ByteLength(text) {
  return unescape(encodeURIComponent(text)).length
}

function extractNameSuffix(name) {
  return name && name.indexOf(DEVICE_NAME_PREFIX) === 0
    ? name.slice(DEVICE_NAME_PREFIX.length)
    : ''
}

// 车型只影响小程序展示，并按蓝牙 deviceId 为每把钥匙分别保存。
function getVehicleType(deviceId) {
  const typeMap = wx.getStorageSync(STORAGE_VEHICLE_TYPES) || {}
  return typeMap[deviceId] === 'compact' ? 'compact' : 'large'
}

Page({
  data: {
    bluetoothReady: false,
    connected: false,
    connecting: false,
    scanning: false,
    deviceId: '',
    deviceName: '尚未连接车辆',
    savedDevice: null,
    devices: [],
    showDevices: false,
    showSettings: false,
    secret: '',
    maskedSecret: '',
    vehicleSuffix: '',
    vehicleType: 'large',
    locked: true,
    powered: false,
    battery: 78,
    rssi: -100,
    signalText: '--',
    statusText: '点击连接你的车辆',
    lastAction: '等待连接',
    busyCommand: '',
    navTop: 88
  },

  onLoad() {
    this.pendingRename = null
    this.renameTimeout = null
    /*
     * 自定义导航栏放在微信胶囊按钮下方，避免不同机型的状态栏高度
     * 导致车辆名称或设置按钮与系统胶囊重叠。
     */
    const menuButton = wx.getMenuButtonBoundingClientRect
      ? wx.getMenuButtonBoundingClientRect()
      : null
    const savedDevice = wx.getStorageSync(STORAGE_DEVICE) || null
    const secret = wx.getStorageSync(STORAGE_SECRET) || ''
    this.setData({
      savedDevice,
      secret,
      navTop: menuButton && menuButton.bottom ? menuButton.bottom + 8 : 88,
      vehicleSuffix: extractNameSuffix(savedDevice && savedDevice.name),
      vehicleType: getVehicleType(savedDevice && savedDevice.deviceId),
      maskedSecret: secret ? '••••••••' : '未设置配对密钥'
    })
    this.bindBleEvents()
    this.prepareBluetooth(Boolean(savedDevice))
  },

  onShow() {
    if (this.data.bluetoothReady && !this.data.connected && this.data.savedDevice) {
      this.connectDevice(this.data.savedDevice, true)
    }
  },

  onUnload() {
    if (this.renameTimeout) clearTimeout(this.renameTimeout)
    bleKey.stopDiscovery()
    bleKey.disconnect()
  },

  bindBleEvents() {
    bleKey.onConnectionChange((connected) => {
      if (!connected) {
        this.setData({
          connected: false,
          connecting: false,
          powered: false,
          statusText: '连接已断开，点击重新连接',
          signalText: '--',
          lastAction: '车辆已离线'
        })
      }
    })

    bleKey.onMessage((message) => {
      const updates = {}
      if (typeof message.locked === 'boolean') updates.locked = message.locked
      if (typeof message.powered === 'boolean') updates.powered = message.powered
      if (typeof message.battery === 'number') updates.battery = message.battery
      if (typeof message.rssi === 'number') {
        updates.rssi = message.rssi
        updates.signalText = this.signalLabel(message.rssi)
      }
      if (
        typeof message.name === 'string' &&
        message.name.indexOf(DEVICE_NAME_PREFIX) === 0
      ) {
        /*
         * 状态响应始终携带 ESP32 当前名称，因此换手机连接或设备重启后，
         * 小程序也能以 ESP32 的 NVS 数据为准同步本地显示名称。
         */
        const savedDevice = Object.assign({}, this.data.savedDevice || {}, {
          deviceId: this.data.deviceId,
          name: message.name
        })
        updates.deviceName = message.name
        updates.vehicleSuffix = extractNameSuffix(message.name)
        updates.savedDevice = savedDevice
        wx.setStorageSync(STORAGE_DEVICE, savedDevice)
      }
      if (message.message) updates.lastAction = message.message
      this.setData(updates)
      this.handleRenameResponse(message)
    })
  },

  async prepareBluetooth(autoConnect) {
    try {
      await bleKey.openAdapter()
      this.setData({ bluetoothReady: true })
      if (autoConnect && this.data.savedDevice) {
        this.connectDevice(this.data.savedDevice, true)
      }
    } catch (error) {
      this.setData({
        bluetoothReady: false,
        statusText: '请开启手机蓝牙后重试'
      })
      wx.showModal({
        title: '蓝牙不可用',
        content: '请开启手机蓝牙，并允许微信使用附近设备权限。',
        showCancel: false
      })
    }
  },

  async openDeviceList() {
    if (this.data.connecting) return
    if (!this.data.bluetoothReady) {
      await this.prepareBluetooth(false)
      if (!this.data.bluetoothReady) return
    }
    this.setData({ showDevices: true, devices: [], scanning: true })
    try {
      await bleKey.stopDiscovery()
      await bleKey.startDiscovery((device) => this.mergeDevice(device))
    } catch (error) {
      this.setData({ scanning: false })
      this.showError(error, '扫描失败')
    }
  },

  mergeDevice(device) {
    const devices = this.data.devices.slice()
    const index = devices.findIndex((item) => item.deviceId === device.deviceId)
    const normalized = {
      deviceId: device.deviceId,
      name: device.name,
      RSSI: device.RSSI || -100,
      signalText: this.signalLabel(device.RSSI || -100)
    }
    if (index >= 0) {
      devices[index] = normalized
    } else {
      devices.push(normalized)
    }
    devices.sort((a, b) => b.RSSI - a.RSSI)
    this.setData({ devices })
  },

  async selectDevice(event) {
    const device = event.currentTarget.dataset.device
    this.closeDeviceList()
    if (
      this.data.connected &&
      this.data.deviceId &&
      this.data.deviceId !== device.deviceId
    ) {
      // 车辆选择入口支持直接切换钥匙，先安全断开当前 BLE 连接。
      await bleKey.disconnect()
      this.setData({
        connected: false,
        powered: false,
        deviceId: ''
      })
    }
    this.connectDevice(device, false)
  },

  async connectDevice(device, silent) {
    if (!device || this.data.connecting || this.data.connected) return
    this.setData({
      connecting: true,
      deviceName: device.name || 'TianlKey',
      statusText: '正在建立安全连接…'
    })
    try {
      await bleKey.stopDiscovery()
      await bleKey.connect(device.deviceId)
      const savedDevice = { deviceId: device.deviceId, name: device.name || 'TianlKey' }
      wx.setStorageSync(STORAGE_DEVICE, savedDevice)
      getApp().globalData.connectedDeviceId = device.deviceId
      this.setData({
        connected: true,
        connecting: false,
        deviceId: device.deviceId,
        deviceName: savedDevice.name,
        savedDevice,
        vehicleType: getVehicleType(savedDevice.deviceId),
        statusText: '车辆在通信范围内',
        lastAction: '钥匙已连接',
        rssi: device.RSSI || -65,
        signalText: this.signalLabel(device.RSSI || -65)
      })
      wx.vibrateShort({ type: 'light' })
      if (!silent) {
        wx.showToast({ title: '蓝牙已连接', icon: 'success' })
      }
      await this.sendCommand('status', true)
    } catch (error) {
      await bleKey.disconnect()
      this.setData({
        connected: false,
        connecting: false,
        statusText: '连接失败，点击重新连接'
      })
      if (!silent) this.showConnectionError(error)
    }
  },

  /**
   * 蓝牙图标只负责连接和断开，重命名由旁边的齿轮图标处理。
   */
  handleBluetoothConnection() {
    if (this.data.connecting) {
      wx.showToast({ title: '正在连接蓝牙', icon: 'none' })
      return
    }
    if (this.data.connected) {
      this.disconnectVehicle()
    } else if (this.data.savedDevice) {
      // 先给出可见提示，随后按钮会通过 connecting 状态切换为蓝色动画。
      wx.showToast({ title: '正在连接蓝牙', icon: 'loading', duration: 1000 })
      this.connectDevice(this.data.savedDevice, false)
    } else {
      this.openDeviceList()
    }
  },

  /**
   * 主动断开车辆蓝牙，并立即同步首页显示状态。
   */
  async disconnectVehicle() {
    if (!this.data.connected) return
    try {
      await bleKey.disconnect()
      getApp().globalData.connectedDeviceId = ''
      this.setData({
        connected: false,
        connecting: false,
        powered: false,
        deviceId: '',
        signalText: '--',
        statusText: '蓝牙连接已断开',
        lastAction: '车辆已离线'
      })
      wx.showToast({ title: '蓝牙已断开', icon: 'none' })
    } catch (error) {
      this.showError(error, '断开失败')
    }
  },

  /**
   * 重命名必须通过 BLE 同步到 ESP32，因此仅允许在连接后进入。
   */
  openRenameSettings() {
    if (!this.data.savedDevice) {
      wx.showToast({ title: '请先添加车辆', icon: 'none' })
      return
    }
    if (!this.data.connected) {
      wx.showToast({ title: '请先连接蓝牙再重命名', icon: 'none' })
      return
    }
    this.openSettings()
  },

  async togglePower() {
    if (!this.data.connected) {
      // 中央钥匙按钮移除后，启动车辆按钮同时承担快捷连接入口。
      if (this.data.savedDevice) {
        this.connectDevice(this.data.savedDevice, false)
      } else {
        this.openDeviceList()
      }
      return
    }
    if (!this.data.secret) {
      this.openSettings()
      wx.showToast({ title: '请先设置配对密钥', icon: 'none' })
      return
    }
    if (!this.data.powered) {
      const confirmed = await this.confirm(
        '启动车辆',
        '请确认车辆已停稳且周围安全。启动后车辆将进入可骑行状态。'
      )
      if (!confirmed) return
    }
    this.sendCommand(this.data.powered ? 'power_off' : 'power_on')
  },

  findVehicle() {
    if (!this.ensureConnected()) return
    this.sendCommand('find')
  },

  refreshStatus() {
    if (!this.ensureConnected()) return
    this.sendCommand('status')
  },

  async sendCommand(command, quiet) {
    if (!this.data.connected) return
    if (!this.data.secret && command !== 'status') {
      this.openSettings()
      wx.showToast({ title: '请先设置配对密钥', icon: 'none' })
      return
    }
    this.setData({ busyCommand: command })
    try {
      await bleKey.send(command, this.data.secret)
      const optimistic = { busyCommand: '' }
      if (command === 'lock') {
        optimistic.locked = true
        optimistic.powered = false
        optimistic.lastAction = '车辆已上锁'
      }
      if (command === 'unlock') {
        optimistic.locked = false
        optimistic.lastAction = '车辆已解锁'
      }
      if (command === 'power_on') {
        optimistic.powered = true
        optimistic.locked = false
        optimistic.lastAction = '车辆已启动'
      }
      if (command === 'power_off') {
        optimistic.powered = false
        optimistic.lastAction = '车辆已关闭'
      }
      if (command === 'find') optimistic.lastAction = '车辆正在鸣笛闪灯'
      this.setData(optimistic)
      if (!quiet) {
        wx.vibrateShort({ type: 'medium' })
        wx.showToast({ title: optimistic.lastAction || '指令已发送', icon: 'none' })
      }
    } catch (error) {
      this.setData({ busyCommand: '' })
      this.showError(error, '发送失败')
    }
  },

  ensureConnected() {
    if (this.data.connected) return true
    wx.showToast({ title: '请先连接车辆', icon: 'none' })
    return false
  },

  openSettings() {
    this.setData({
      showSettings: true,
      vehicleSuffix: extractNameSuffix(
        this.data.deviceName ||
        (this.data.savedDevice && this.data.savedDevice.name)
      )
    })
  },

  closeSettings() {
    this.setData({ showSettings: false })
  },

  onSecretInput(event) {
    this.setData({ secret: event.detail.value.trim() })
  },

  onVehicleSuffixInput(event) {
    this.setData({ vehicleSuffix: event.detail.value.trim() })
  },

  selectVehicleType(event) {
    const vehicleType = event.currentTarget.dataset.type
    if (vehicleType !== 'large' && vehicleType !== 'compact') return

    const deviceId = this.data.deviceId ||
      (this.data.savedDevice && this.data.savedDevice.deviceId)
    if (!deviceId) {
      wx.showToast({ title: '请先添加车辆', icon: 'none' })
      return
    }

    const typeMap = wx.getStorageSync(STORAGE_VEHICLE_TYPES) || {}
    typeMap[deviceId] = vehicleType
    wx.setStorageSync(STORAGE_VEHICLE_TYPES, typeMap)
    this.setData({ vehicleType })
    wx.showToast({
      title: vehicleType === 'compact' ? '已选择小车' : '已选择大车',
      icon: 'none'
    })
  },

  async renameVehicle() {
    if (!this.ensureConnected()) return
    if (!this.data.secret) {
      wx.showToast({ title: '请先设置配对密钥', icon: 'none' })
      return
    }

    const suffix = this.data.vehicleSuffix.trim()
    const suffixBytes = utf8ByteLength(suffix)
    if (!suffix || suffixBytes > NAME_SUFFIX_MAX_BYTES) {
      wx.showToast({
        title: '后缀需为 1–12 个字节',
        icon: 'none'
      })
      return
    }
    if (/["\\\r\n]/.test(suffix)) {
      wx.showToast({ title: '名称不能包含引号或反斜杠', icon: 'none' })
      return
    }

    this.setData({ busyCommand: 'rename' })
    // 先登记等待状态，避免 ESP32 回应快于 GATT 写入成功回调而丢失结果。
    this.startRenameWait(suffix)
    try {
      await bleKey.send('rename', this.data.secret, { name: suffix })
    } catch (error) {
      this.cancelRenameWait()
      this.setData({ busyCommand: '' })
      this.showError(error, '重命名失败')
    }
  },

  /**
   * 等待 ESP32 对重命名指令做业务确认。写入成功不代表密钥校验、
   * 名称校验和 NVS 提交成功，因此不能在 write 回调中直接显示成功。
   */
  startRenameWait(suffix) {
    this.cancelRenameWait()
    this.pendingRename = suffix
    this.renameTimeout = setTimeout(() => {
      this.pendingRename = null
      this.renameTimeout = null
      this.setData({ busyCommand: '' })
      wx.showModal({
        title: '车辆没有回应',
        content: '请确认 ESP32 已烧录支持重命名的新固件，并检查蓝牙连接后重试。',
        showCancel: false,
        confirmColor: '#14B86E'
      })
    }, 5000)
  },

  cancelRenameWait() {
    if (this.renameTimeout) clearTimeout(this.renameTimeout)
    this.renameTimeout = null
    this.pendingRename = null
  },

  handleRenameResponse(message) {
    if (!this.pendingRename || typeof message.ok !== 'boolean') return

    /*
     * 新固件会返回 action=rename；旧固件或密钥错误会返回失败响应但没有
     * action。普通的成功状态响应不应误判为重命名确认。
     */
    if (message.action !== 'rename' && message.ok) return

    const expectedName = DEVICE_NAME_PREFIX + this.pendingRename
    this.cancelRenameWait()
    this.setData({ busyCommand: '' })

    if (message.ok && message.name === expectedName) {
      wx.showToast({ title: '车辆名称已保存', icon: 'success' })
      return
    }

    const reason = message.message || 'ESP32 未接受新的车辆名称'
    const currentName = message.name
      ? `\n当前名称：${message.name}`
      : ''
    wx.showModal({
      title: '重命名失败',
      content: `${reason}${currentName}\n请确认小程序配对密钥与固件一致。`,
      showCancel: false,
      confirmColor: '#14B86E'
    })
  },

  saveSettings() {
    if (this.data.secret.length < 6) {
      wx.showToast({ title: '密钥至少需要 6 位', icon: 'none' })
      return
    }
    wx.setStorageSync(STORAGE_SECRET, this.data.secret)
    this.setData({
      showSettings: false,
      maskedSecret: '••••••••'
    })
    wx.showToast({ title: '配对密钥已保存', icon: 'success' })
  },

  async forgetVehicle() {
    const confirmed = await this.confirm('移除车辆', '将断开连接并清除已保存的车辆信息。')
    if (!confirmed) return
    const forgottenDeviceId = this.data.deviceId ||
      (this.data.savedDevice && this.data.savedDevice.deviceId)
    await bleKey.disconnect()
    wx.removeStorageSync(STORAGE_DEVICE)
    if (forgottenDeviceId) {
      // 移除钥匙时同步清理这辆车单独保存的外观选择。
      const typeMap = wx.getStorageSync(STORAGE_VEHICLE_TYPES) || {}
      delete typeMap[forgottenDeviceId]
      wx.setStorageSync(STORAGE_VEHICLE_TYPES, typeMap)
    }
    this.setData({
      connected: false,
      savedDevice: null,
      deviceId: '',
      deviceName: '尚未连接车辆',
      vehicleType: 'large',
      showSettings: false,
      statusText: '点击连接你的车辆',
      lastAction: '等待连接'
    })
  },

  closeDeviceList() {
    bleKey.stopDiscovery()
    this.setData({ showDevices: false, scanning: false })
  },

  stopPropagation() {},

  signalLabel(rssi) {
    if (rssi >= -60) return '很强'
    if (rssi >= -72) return '良好'
    if (rssi >= -85) return '较弱'
    return '微弱'
  },

  confirm(title, content) {
    return new Promise((resolve) => {
      wx.showModal({
        title,
        content,
        confirmColor: '#14B86E',
        success: (result) => resolve(result.confirm),
        fail: () => resolve(false)
      })
    })
  },

  showError(error, fallback) {
    const message = error && (error.errMsg || error.message)
    wx.showToast({ title: message || fallback, icon: 'none', duration: 2500 })
  },

  /**
   * 蓝牙错误通常包含 UUID 或系统错误码，Toast 会截断这些关键信息，
   * 因此手动连接失败时使用弹窗完整展示，方便现场排查。
   */
  showConnectionError(error) {
    const content = error && (error.message || error.errMsg)
    wx.showModal({
      title: '车辆连接失败',
      content: content || '请确认 ESP32 已通电并重新尝试。',
      showCancel: false,
      confirmText: '知道了',
      confirmColor: '#14B86E'
    })
  }
})
