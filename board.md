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
- [ ] :feature-modbus 同上 + token 换皮
- [ ] :app 外壳、模式切换、last_mode
- [ ] 测试 / Roborazzi / CI / docs
