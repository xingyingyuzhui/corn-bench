# Corn Bench 外壳（第一期）

合并后唯一的 Android 应用：`:app` 提供 Application / Activity 与模式外壳，两个功能模块都变成 library。
本文件只记录第一期实际落地的结构和**还没验证**的部分。

## 包与启动

- applicationId / namespace 都是 `dev.corn.bench`；versionName `0.2.0` / versionCode `1`；minSdk 29。
- `BenchApplication` 只做一件轻的事：`FlashingFeature.install(this)`（批量烧录要知道 App 是否在前台）。
  两个模式的容器（Room、USB 仓库、DataStore）都在第一次进入各自模式时才创建，
  冷启动停在一边不会初始化另一边。
- `MainActivity` 是唯一 Activity（`singleTop`）。两个模式都是同一个 Activity 里的 Compose 子图，切换不重启。
- 插入 USB 设备（`USB_DEVICE_ATTACHED`）只唤起 App 并提示 5 秒“不会自动切换模式”，不自动进任何模式。

## 模式与 last_mode

- `bench_ui` DataStore 里存 `last_mode` = `BenchMode.key`（`"modbus"` / `"flashing"`），不存 ordinal。
  三个 DataStore 文件分开：`bench_ui`（外壳）、`flashing_ui`（烧录）、`ui_settings`（上位机）。
- 启动读盘期间显示空底（`bench-loading`），不先闪一下选择页；读到值直接进上次的模式，
  没有值（首次启动）显示全屏选择页（`ModePickerScreen`）。
- 选择 / 切换成功后立刻写回 `last_mode`。

## 切换守卫

`ModeGuard` 有两个动作：

| 方法 | 作用 |
| --- | --- |
| `blockedReason(mode)` | 不能离开这个模式的原因；null = 可以离开。**只查询，不创建容器。** |
| `release(mode)` | 离开前释放资源（停轮询、断串口、停“等待下一块板”）；false = 超时，不切换。 |

- 上位机：会话运行中 / 正在请求 USB 授权 / 正在打开 / 正在断开时不能离开；空闲或出错可以。
- 烧录：单片烧录中、批次未结束（含暂停 / 已烧完未结束）不能离开；`releaseUsb()` 停掉等待下一块板的轮询，
  并等正在识别芯片的操作收尾（默认 5 秒超时）。
- 被拦截时目标卡置灰并写明原因，同时弹一个只读的确认框；不提供“强制断开”。
- 每个模式一个独立 `ViewModelStore`，切走时 `clear()`，切回来是新的 ViewModel。

## 外观

- 第一期只开放深色：外壳固定 `CornTheme(dark = true)`；上位机侧 `PHASE1_DARK_ONLY` 把读到的主题归一成 DARK，
  外观页隐藏“主题 / 强调色”两项（代码和浅色 token 都保留，第二期把常量改成 false 并恢复两行即可）。
- 颜色、间距、字号来自 `:ui-common` 的语义 token；上位机自己的间距刻度（group 16 / card 12）先保留，
  避免换皮时整页节奏一起变。
- 字号只在 `CornTheme` 里叠一次（系统倍率 × App 倍率），`ModbusThemeDensityTest` 锁定 1.3 不会变成 1.69。
- 系统栏图标深浅由外壳 `BenchSystemBars()` 按当前 token 设置（`BenchSystemBarsTest` 锁定系统浅色时仍是浅色图标）。

## 本地已验证 / 未验证

已验证（本机，2026-10-27）：

- 5 个模块单测全绿，共 1210 个：`:modbus-core` 58、`:flashing-core` 164、`:feature-modbus` 948、`:feature-flashing` 22、`:app` 18。
- `:app` / `:feature-modbus` / `:feature-flashing` 的 `lintDebug` 通过（`abortOnError = true`）。
- `:app:assembleDebug` / `:app:assembleRelease` 通过；`tools/modbus/verify_offline_apks.py` 通过；
  合并后的 debug / release manifest 都没有 `INTERNET`。
- `:app:recordRoborazziDebug` / `:feature-modbus:recordRoborazziDebug` 能出图（模式选择页、切换面板、拦截态）。

未验证：

- **真机 / 模拟器的模式切换、USB 授权、前后台切换**：本机 `adb devices` 无设备，全部只有 Robolectric 渲染与单测。
- 真实 ST-LINK 烧录与 USB-RS485 收发（第一期本来就没有硬件）。
- GitHub Actions 上的最终结果（这轮推送后的 run 见 PR #1）。
