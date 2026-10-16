# Modbus Mobile

Android 离线 Modbus RTU 主站 / 从站调试器。本期在工程骨架上加上多站总线帧扫描、回显跟踪，以及上次审阅留下的协议修复。真机和多站总线未测。

规范只看 `docs/spec.md`。`research/` 没有放进仓库。

## 工具链

| 项 | 版本 |
|---|---|
| JDK | 17 |
| Gradle | 9.6.0 |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.4.21 |
| Compose BOM | 2026.09.00 |
| compileSdk | 37.2 |
| targetSdk | 37 |
| minSdk | 26 |
| usb-serial-for-android | 3.11.0 |
| kotlinx-coroutines | 1.11.0 |

稳定 API 是 37。`compileSdk` 用平台 `android-37.2`，因为当前 Compose BOM、Navigation 和 Lifecycle 要求至少编译到 API 37。AGP 9.4 的 `targetSdk` 只接受主版本，所以目标版本是 37。

## 目录

```
app/            Compose、USB、前台服务、会话
modbus-core/    纯 JVM 协议栈，不能依赖 android.*
docs/           V5.1 规格、路线、审阅记录、变更说明
```

## 构建

```bash
./gradlew :modbus-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

演示模式不需要转换器。连接页选「演示设备」：主站可读 Unit 1 的 HR0–9（0、10、20…90）；从站的 HR0 每 5 秒被演示主站改一次。

## 本期没有做

FTDI 延迟界面、Room、导入导出、曲线、数据类型转换、Hilt，以及任何网络权限。多站扫描和分块回显已在协议核心里实现；真机和多站总线未测。
