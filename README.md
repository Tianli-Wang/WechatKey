# TianlKey 电动车数字钥匙系统 (多端仓库)

基于低功耗蓝牙（BLE）连接 ESP32 固件，将手机作为电动车无感数字钥匙。本仓库包含 **微信小程序端** 与 **Android 原生客户端**。

---

## 目录结构

```text
WechatKey/
├── android/                 # Android 原生客户端 (Kotlin + Jetpack Compose)
│   ├── app/
│   │   ├── src/main/java/   # 业务代码 (BLE 协议、ViewModel、Compose UI 等)
│   │   ├── src/main/res/    # 资源文件 (Drawable、布局、颜色、主题)
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── wechat/                  # 微信小程序端
│   ├── pages/index/         # 首页逻辑与界面
│   ├── utils/ble.js         # 小程序 BLE 协议封装
│   ├── assets/              # 图标与车型插图
│   ├── app.json
│   └── project.config.json
│
├── project.config.json      # 根目录微信开发者工具重定向配置
└── README.md
```

---

## BLE 协议规范

UUID 定义如下（必须与 ESP32 固件中的配置保持一致）：

| 用途 | UUID | 说明 |
| :--- | :--- | :--- |
| **Service (主服务)** | `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` | 广播需携带该服务 UUID |
| **Write (手机写入)** | `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` | 支持 Write 或 WriteWithoutResponse |
| **Notify (ESP32通知)** | `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` | 支持 Notify 或 Indicate |

- **广播名称**：以 `TianlKey` 开头（如 `TianlKey-01` 或 `TianlKey-我的车`）。
- **数据帧格式**：单行 UTF-8 JSON 格式，**以换行符 `\n` 结尾**。

### 1. 手机发送指令 (Write)

```json
{"cmd":"unlock","key":"YOUR_SECRET","ts":1785220000}
```

支持的指令：
- `status`: 查询车辆实时状态
- `lock`: 车辆上锁
- `unlock`: 车辆解锁
- `power_on`: 启动车辆动力
- `power_off`: 关闭车辆动力
- `find`: 寻车（鸣笛闪灯）
- `rename`: 重命名车辆后缀（携带 `"name":"后缀"`，后缀上限 12 个 UTF-8 字节）

### 2. ESP32 上报状态 (Notify)

```json
{"locked":true,"powered":false,"battery":78,"rssi":-64,"name":"TianlKey-我的车","message":"状态已更新","ok":true,"action":"rename"}
```

---

## 客户端功能与运行说明

### 1. Android 原生客户端 (`android/`)
- **技术栈**：Kotlin + Jetpack Compose (Material 3) + Android BLE GATT API
- **核心功能**：
  - 自动申请 Android 12+ (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`) 与低版本蓝牙/定位运行时权限
  - 扫描筛选 `TianlKey` 设备并按信号强度（RSSI）实时排序
  - MTU 协商（自动拓展至 512 字节避免长数据截断）
  - 换行符 `\n` 粘包/分包完整帧解析缓冲
  - 支持“大车”/“小车”外观切换
  - 配对密钥本地存储与校验
  - 启动车辆前安全警示二次确认弹窗
  - 车辆重命名校验与 5 秒超时反馈
- **开发与运行**：
  1. 使用 **Android Studio** 打开 `android/` 目录。
  2. 等待 Gradle 同步完成（推荐 Gradle 8.4+，JDK 17）。
  3. 连接 Android 真机（需打开蓝牙与定位），直接点击 **Run 'app'** 运行调试。

### 2. 微信小程序端 (`wechat/`)
- **运行说明**：
  - 使用 **微信开发者工具** 打开根目录 `WechatKey/`（已配置 `miniprogramRoot: "wechat/"`）或直接打开 `wechat/` 目录。
  - BLE 功能在模拟器中无法进行真实通信，请使用“**真机调试**”测试。
