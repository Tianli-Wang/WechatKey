# TianlKey 微信小程序

用微信小程序通过 BLE（低功耗蓝牙）连接 ESP32，将手机作为电动车数字钥匙。

## 当前功能

- 扫描名称以 `TianlKey` 开头的 ESP32
- 保存车辆并在下次打开时自动重连
- 启动车辆、关闭车辆、鸣笛闪灯
- 在小程序中修改 `TianlKey-` 后缀并同步保存到 ESP32
- 可为每把钥匙分别选择“大车”或“小车”外观
- 接收车辆电量、锁定状态、通电状态和信号强度
- 配对密钥保存在微信本地存储中

> 微信开发者工具的模拟器无法完成真实 BLE 通信，请使用“真机调试”测试。

车型外观以蓝牙 `deviceId` 为键保存在当前手机本地，只影响小程序中的车辆
主视觉，不修改 ESP32 控制协议。点击首页顶部车辆名称可扫描或切换车辆，
右侧依次为蓝牙连接、重命名和状态刷新图标。

连接过程会先读取 ESP32 实际暴露的 GATT 服务和特征，再开启状态通知。
针对 iOS 在连接回调后服务尚未准备完成的情况，小程序会自动等待并重试。
若连接失败，弹窗会显示具体阶段和系统错误码。

## BLE UUID

UUID 集中定义在 `utils/ble.js`：

| 用途 | UUID |
| --- | --- |
| Service | `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` |
| 小程序写入 | `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` |
| ESP32 通知 | `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` |

ESP32 的广播名称需要以 `TianlKey` 开头，例如 `TianlKey-01`，并在广播数据中包含上面的 Service UUID。

## 通信协议

小程序向写特征发送一行 UTF-8 JSON（以 `\n` 结尾）：

```json
{"cmd":"unlock","key":"YOUR_SECRET","ts":1785220000}
```

`cmd` 支持：

- `status`
- `lock`
- `unlock`
- `power_on`
- `power_off`
- `find`
- `rename`

重命名指令额外携带 `name` 字段，此字段只表示名称后缀：

```json
{"cmd":"rename","key":"YOUR_SECRET","ts":1785220000,"name":"我的车"}
```

后缀最多为 12 个 UTF-8 字节。ESP32 会将其写入 NVS，断电重启后继续使用；
固定前缀 `TianlKey-` 不允许通过小程序修改。

ESP32 通过通知特征返回一行 JSON：

```json
{"locked":true,"powered":false,"battery":78,"rssi":-64,"name":"TianlKey-我的车","message":"状态已更新"}
```

## 安全说明

当前 `key + timestamp` 协议用于原型联调，不能抵抗重放攻击。正式用于车辆前，应让 ESP32 发送随机挑战值，并使用 HMAC-SHA256 对 `challenge + cmd + timestamp` 签名；同时设置指令过期时间、错误次数限制和物理配对流程。不要把固定密钥直接写进公开代码仓库。
