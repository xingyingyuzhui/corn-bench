# Corn Bench

Corn Bench 是把 **Corn Modbus**（Android 上的 Modbus RTU 上位机 / 从站模拟）和
**Corn Flashing**（基于 ST-LINK 的 STM32 离线烧录）合并成的一个 Android 应用，
同一时刻只运行其中一个模式（上位机 / 烧录）。

> 非商业授权：PolyForm Noncommercial 1.0.0，见 [LICENSE.md](LICENSE.md) 与 [NOTICE](NOTICE)。
> 应用不申请 INTERNET 权限。

## 模块

| 模块 | 说明 |
| --- | --- |
| `:app` | Corn Bench 外壳：唯一 Application / Activity、模式选择与切换、`last_mode`（`dev.corn.bench`） |
| `:ui-common` | 共享设计系统：色板 → 语义色 → 间距 / 圆角 / 字号，外壳组件与模式切换 UI（`dev.corn.ui`） |
| `:feature-flashing` | 烧录模式（原 Corn Flashing `:app`，含 NDK usbfs 原生代码） |
| `:flashing-core` | 烧录核心逻辑（原 Corn Flashing `:core`，纯 JVM） |
| `:feature-modbus` | 上位机模式（原 Corn Modbus `:app`） |
| `:modbus-core` | Modbus 协议核心（纯 JVM） |

两个源仓库的完整 git 历史都已合入本仓库（`git log --follow` 可追到原始提交）。
原 README 见 [docs/flashing/README.md](docs/flashing/README.md) 与 [docs/modbus/README.md](docs/modbus/README.md)。

第一期（`feat/phase1-shell`）的外壳结构、模式切换守卫与「本地已验证 / 未验证」清单见
[docs/bench-shell.md](docs/bench-shell.md)。

## 构建

需要 JDK 17+、Android SDK（compileSdk 37.2）、NDK 27.2.12479018、CMake 3.22.1。

```bash
# 单测（两个纯 JVM core + 两个 feature + 外壳）
./gradlew :modbus-core:test :flashing-core:test \
  :feature-modbus:testDebugUnitTest :feature-flashing:testDebugUnitTest :app:testDebugUnitTest

# 整包
./gradlew :app:assembleDebug
```

本机没有 NDK 时，含原生代码的 `:feature-flashing` 构建会失败；CI 里会装 NDK / CMake。

