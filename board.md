# Corn Bench 里程碑看板

每个里程碑（推送 / 本地构建 / CI 结果）都记在这里。没验证的写“未验证”。

## 2026-10-27 初始导入

- 源仓库可见性：`xingyingyuzhui/Corn-Flashing` PUBLIC，`xingyingyuzhui/corn-modbus` PUBLIC → 新仓库 `xingyingyuzhui/corn-bench` 建为 **public**。
- 历史导入：本机无 git-filter-repo，改用“临时克隆里一次 `git mv` 提交 + `git merge --allow-unrelated-histories`”。
  - Corn Flashing main @ 24bc94f（55 个提交）→ 移动提交 9a05794
  - Corn Modbus main @ a0a0b98（175 个提交）→ 移动提交 6a7f449
  - 合并提交 f877666（Modbus）、aae066e（Flashing）；`git log --follow` 可追到两边最早的提交。
- 根配置：settings（4 个导入模块）、catalog 插件、合并 .gitignore、LICENSE.md 补 Required Notice、合并 NOTICE、新 README。
- 本地构建（本机无 NDK / CMake，只跑纯 JVM 模块）：
  - `:flashing-core:test` 164 个通过，`:modbus-core:test` 58 个通过（0 失败）。
  - 含原生代码的 `:feature-flashing` 本地未构建，交给 GitHub CI。

## 2026-10-27 第一期 · 分支 feat/phase1-shell

- 决策（老板已确认）：applicationId `dev.corn.bench`、versionName 0.2.0 / versionCode 1、minSdk 29、第一期只开放深色（可调项隐藏不删）、TX 色由开发选定。
- [x] 构建结构：catalog 补 `android-library`；6 个模块（:app、:ui-common、:feature-flashing、:flashing-core、:feature-modbus、:modbus-core）；两个 feature 改为 library，Flashing 依赖改走 catalog。
- [x] :ui-common token 初版（色板 → 语义 → 尺寸）+ 外壳组件；本地 `:ui-common:compileDebugKotlin` 通过。
  - TX 色：青绿 #4FD1C5（与玉米 #F7C948 ΔE≈83；原 Modbus 琥珀 #F5A524 与玉米 ΔE≈19）。
- [x] :feature-flashing 去 Activity / Application（`FlashingFeature` 懒加载容器 + `FlashingEntry`），USB 授权 action 改为 `<包名>.flashing.USB_PERMISSION`，SharedPreferences `ui` → `flashing_ui`，`CornColors` 改由 token 取值（`CornColorsMatchTokensTest` 锁定旧值）。
  - 本机补齐原生构建：SDK 已有 NDK 27.2.12479018；CMake 3.22.1 + ninja 用 pip 装在 /home/box/tc/tools/cmake-venv，经 local.properties 的 `cmake.dir` 指向（不入库）。
  - 本地：`:feature-flashing:compileDebugKotlin` + `externalNativeBuildDebug` 通过；`:feature-flashing:testDebugUnitTest` 22 个通过（含新增 1 个），加 `:flashing-core:test` 164 个，共 186 个。
- [x] :feature-modbus 去 Activity / Application（`ModbusFeature` / `ModbusContainer` 懒加载 + `ModbusEntry`），`ModbusColors` 改由 :ui-common token 取值（`ModbusColorsMatchTokensTest` 锁定映射），页头接模式切换器，USB 授权 action 改为 `<包名>.modbus.USB_PERMISSION`，第一期主题归一为深色（`PHASE1_DARK_ONLY`），外观页隐藏主题 / 强调色两项（不删代码）。
  - 本地：`:feature-modbus:compileDebugKotlin` 通过；`:feature-modbus:testDebugUnitTest` 946 个通过（0 失败，含新增 4 个）。
  - 收尾修正：`ModbusTheme` 原来自己 `provides LocalDensity(fontScale × appScale)`，外面再套 :ui-common 的 `CornTheme`（它乘一次），App 字号被叠两次（1.3 → 1.69）。现只由 `CornTheme` 叠一次，新增 `ModbusThemeDensityTest` 锁定（故意改回双乘时该测试会红）。
  - 收尾修正：横屏 `FloatingRail` 里再加一个模式切换器会和页头里的重复（同屏两个 `brand-switcher`），已去掉，只保留页头一个；`AppBar` 重复的 `layout.size` import 一并清掉。
- [x] :app 外壳、模式切换、last_mode
  - `BenchApplication` 只登记 `FlashingFeature.install`（前台状态跟踪）；`:app` 唯一 `MainActivity`，两个模式都是 Compose 子图，切换不重启 Activity。
  - `BenchRoot` / `ModeShell`：`bench_ui` DataStore 的 `last_mode` 决定进哪个模式；没有值时显示全屏选择页；读盘期间显示空底，不闪选择页。
  - `ModeGuard`：`blockedReason()` 只查询不创建容器；`release()` 转给两个模块的 `releaseUsb()`。拦截时卡片置灰写原因，`release()` 返回 false 时不切换。
  - 每个模式一个 `ViewModelStore`，切走时清掉；插入 USB 只弹 5 秒提示「不会自动切换模式」。
  - 本地：`:app:testDebugUnitTest` 18 个通过（`BenchShellTest` 13 + `ModeStoreTest` 4 + `BenchSystemBarsTest` 1，前者含 DataStore key 往返与「守卫不创建容器」）。
- [x] 外壳系统栏 + docs 初稿
  - 合并成一个 Activity 后，原来烧录模式自己设的「固定浅色图标」没有了：外壳 `BenchSystemBars()` 按当前 token 设置系统栏图标深浅，
    `BenchSystemBarsTest` 先模拟 `enableEdgeToEdge()` 在系统浅色下给出深色图标，再断言外壳改回浅色。
  - `docs/bench-shell.md` 记录外壳结构、切换守卫与「本地已验证 / 未验证」清单；README 补全 6 个模块与构建命令。
- [ ] 测试 / Roborazzi / CI / docs 收尾（CI 结果待这轮推送后确认；Roborazzi 只本地出图，未做像素基线）
