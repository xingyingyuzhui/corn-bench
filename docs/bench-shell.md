# Corn Bench 外壳（第一期 + 第二期外观）

合并后唯一的 Android 应用：`:app` 提供 Application / Activity 与模式外壳，两个功能模块都变成 library。
本文件记录第一期的结构与第二期开放的浅色 / 强调色，以及**还没验证**的部分。

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
- 判定本体是纯函数，单测直接覆盖：`leaveBlockedReasonFor(SessionState)` / `releaseSession(...)`（上位机，`ModbusLeaveGuardTest`）、
  `flashingLeaveBlockedReason(running, phase)` / `releaseFlashingUsb(...)`（烧录，`FlashingLeaveGuardTest`），
  各忙态文案与超时返回 false 都有用例。
- 已知限制：切换面板打开后卡片的置灰是打开那一刻的快照，后台状态变了不会刷新；真正切换时 `requestSwitch` 会重查，不会切错。
- 每个模式一个独立 `ViewModelStore`，切走时 `clear()`，切回来是新的 ViewModel。

## 外观（第二期：浅色 / 强调色开放）

- **单一来源**：外观由外壳持有，存在 `bench_ui`（`theme_mode` / `accent`），经
  `LocalAppearanceController` 提供给两个模式。改完切模式不会换主题；冷启动停在烧录模式也不用
  为了读主题去创建上位机的 DataStore。
- 主题三档：跟随系统 / 浅色 / 深色；强调色五档：玉米（默认）、蓝、绿、青、紫。
  `BenchAccent` 只在 `:ui-common` 定义，两侧共用一个枚举。
- 两个模块都不再自带写死深浅色的调色板：
  - 烧录 `CornColors` 改成随主题解析的 `@Composable` getter，352 处调用点不变；
    `CornColorsThemeTest` 在 Compose 里实际读一遍，浅色下必须真的是浅底深字（故意改回写死深色会红）。
  - 上位机 `ModbusColors` 从 `benchColors(dark, accent)` 取值，`ModbusColorsMatchTokensTest` 锁定映射；
    没有外壳时（预览 / 单测）退回读 `UiSettings.theme` / `UiSettings.accent`。
- 外观页恢复“主题 / 强调色”两项，写入外壳控制器；`PHASE1_DARK_ONLY` 改为 `false`（函数保留为恒等，
  旧调用点与旧截图不用改）。第一期的 `ui_settings` 旧值在第一次启动时迁移到 `bench_ui`，之后以外壳为准。
- 迁移只做一次，用独立的 `appearance_migrated` 标记判断（不能看 `theme_mode` 在不在：旧数据只有 accent 时
  会每次冷启动重迁移、覆盖用户后来的选择）。已有外壳外观值时只补标记不覆盖；读旧值失败不记标记、下次再试；
  旧上位机的 `AMBER` 显式映射为 `CORN`。
- 首帧等读盘：`BenchAppearanceStore.loaded` 为 false 时外壳只放空的 `bench-loading` 占位（露出窗口底色），
  不按默认外观先画一帧。残余：Compose 起来之前的窗口底色仍跟系统深浅。
- 冷启动窗口底色跟随系统深浅（`app/src/main/res/values{,-night}`），浅色主题下不会先闪一下黑屏。
- 颜色、间距、字号仍来自 `:ui-common` 语义 token；上位机自己的间距刻度（group 16 / card 12）先保留。
- 字号只在 `CornTheme` 里叠一次（系统倍率 × App 倍率），`ModbusThemeDensityTest` 锁定 1.3 不会变成 1.69。
- 系统栏图标深浅由外壳 `BenchSystemBars()` 按当前 token 设置（`BenchSystemBarsTest` 锁定系统浅色时仍是浅色图标）。

## 本地已验证 / 未验证

已验证（本机，2026-10-27 / 第二期 2026-10-28）：

- 5 个模块单测全绿，共 1221 个：`:modbus-core` 58、`:flashing-core` 164、`:feature-modbus` 948、`:feature-flashing` 27、`:app` 24。
  - 第二期新增：`CornColorsThemeTest`（浅色下烧录页必须真的是浅底深字、强调色跟着外壳变）、
    `BenchAppearanceTest`（改外壳外观要传到模式内容；切模式保持同一套 token；跟随系统按 uiMode 走）、
    `AppearanceMigrationTest`（外观经 `bench_ui` 持久化；缺键时退回跟随系统 + 玉米）。
- `:app` / `:feature-modbus` / `:feature-flashing` 的 `lintDebug` 通过（`abortOnError = true`）。
- `:app:assembleDebug` / `:app:assembleRelease` 通过；`tools/modbus/verify_offline_apks.py` 通过；
  合并后的 debug / release manifest 都没有 `INTERNET`。
- `:app:recordRoborazziDebug` / `:feature-modbus:recordRoborazziDebug` 能出图（模式选择页、切换面板、拦截态、
  上位机浅色各页）；浅色连接页实测底色 #F6F4EE + 白卡片，不是黑底。

未验证：

- **真机 / 模拟器的模式切换、USB 授权、前后台切换**：本机 `adb devices` 无设备，全部只有 Robolectric 渲染与单测。
- 真实 ST-LINK 烧录与 USB-RS485 收发（第一期本来就没有硬件）。
- GitHub Actions 上的最终结果（这轮推送后的 run 见 PR #1）。
