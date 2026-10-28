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
- [x] 测试 / Roborazzi / CI / docs
  - CI 失败原因查清：`setup-python` 的 `cache-dependency-path` 还指向导入前的 `tools/rtu_lab/requirements.txt`，文件已挪到 `tools/modbus/rtu_lab/`，
    setup 阶段直接报 “No file … matched”。同时把 NDK 27.2.12479018 / CMake 3.22.1 装进 runner，并补跑两个 feature 的单测 + lint + Roborazzi。
  - `35a8977` 的 push run 与 PR run 已 **success**（12 分钟内）；`1e7c977` 的 push/PR run 也 success。
  - 本地：5 个模块单测 1210 个通过（modbus-core 58、flashing-core 164、feature-modbus 948、feature-flashing 22、app 18）；
    `:app` lintDebug + assembleDebug/Release、两个 feature 的 lintDebug 通过；`tools/modbus/verify_offline_apks.py` 通过；
    合并后的 debug/release manifest 均无 `INTERNET`。
  - Roborazzi：`:app:recordRoborazziDebug`（模式选择页 / 切换面板 / 拦截态）与 `:feature-modbus:recordRoborazziDebug` 能出图；
    只做“能渲染”检查，没有像素基线比对，基线 PNG 不入库。
  - docs：新增 `docs/bench-shell.md`（外壳结构与已验证/未验证清单）、`docs/注意事项.md`（合并后新增规则）、README 补全 6 个模块。
- 未验证：真机 / 模拟器上的模式切换、USB 授权、前后台切换（本机 `adb devices` 无设备）；真实 ST-LINK / USB-RS485 收发。

## 2026-10-28 第二期 · 外观开放（浅色 / 强调色）

- 范围：仓库里写明的第二期内容就是「重新开放浅色与强调色」（`PHASE1_DARK_ONLY` 注释、`BenchPalette` 的浅色暂定值、
  `docs/注意事项.md` 第 13 条）。没有其他第二期规格文档，本轮只做这一项，不扩到协议 / 功能。
- [x] `:ui-common` 外观契约：`BenchAccent`（玉米 / 蓝 / 绿 / 青 / 紫，深浅各一套 4 值）、
  `BenchAppearance.accent`、`BenchThemeMode`、`AppearanceController` + `LocalAppearanceController`。
  `benchColors(dark, accent)` 只换强调槽位（`warning` 保持语义色，不跟着强调色变）。
- [x] 烧录侧 `CornColors` 从写死深色的 `object` 改成随主题解析的 `@Composable` getter（352 处调用点不变）：
  `ChipTone` 改为语义枚举 + `colors()`，`Illo` / `Ring` / `hatch()` / `outcomeColor` / `statusColor` 同步改成 Composable。
  新增 `CornColorsThemeTest`：在 Compose 里实际读 `CornColors`，浅色必须是浅底深字、强调色跟外壳变；
  故意把 `Background` 改回写死 `benchColors(true)` 时该测试会红（已实测）。
- [x] 上位机侧：`PHASE1_DARK_ONLY` → `false`，`forPhase1()` 保留为恒等；外观页恢复「主题 / 强调色」两项并写外壳控制器；
  `ModbusTheme` 有外壳时以外壳外观为准，没有时退回 `UiSettings`。
- [x] 外壳：`BenchAppearanceStore`（`bench_ui` 的 `theme_mode` / `accent`）+ 首次启动从第一期 `ui_settings` 迁移旧值；
  `BenchRoot` 统一提供外观，新增 `appearanceController` 注入点给测试。
- [x] 冷启动窗口底色跟随系统深浅（`app/src/main/res/values{,-night}/`），浅色主题下不再先闪黑屏。
- 本地：5 个模块单测 **1221 个通过**（modbus-core 58、flashing-core 164、feature-modbus 948、feature-flashing 27、app 24）；
  三个模块 `lintDebug` 通过；`:app:assembleDebug` / `assembleRelease` 通过；`verify_offline_apks.py` 通过。
  Roborazzi 出图确认浅色连接页是 `#F6F4EE` 底 + 白卡片。
  （这些是第二期改动最后一次完整跑的结果；之后只补了未使用函数清理与看板 / 文档，重跑了 `:app` 与 `:feature-flashing` 单测。）
- 未验证：浅色 / 强调色在真机上的观感与对比度（只有 Robolectric 渲染与单测）；烧录模式浅色下的实机截图；
  真机切换模式 / USB 授权 / 前后台（本机无设备）。

## 2026-10-28 第二期收尾 · 按评审 E 修复后合并 PR #1

- 依据：`corn-bench-review-E-20261028.md` 的 P0 + P1，另加「app 图标换成无角标纯玉米」。
- [x] 图标：`:app` launcher（`ic_launcher` / `_round` / `_foreground` / `_monochrome`，mdpi–xxxhdpi）换成无芯片/闪电角标的纯玉米。
  - 源图 `corn-bench-plain-corn-icon.png` 实为 2048² JPEG，自带深色圆角底；按连通域把底色抠成透明，前景放在 108dp 画布的中间 72dp，
    底色仍走 `@color/ic_launcher_background`（#1A1A1A）。单色图是同轮廓白剪影 + 眼睛镂空。
  - `bench_corn_mark`（:ui-common）与 `corn_logo`（:feature-flashing）同步换成无角标版；透明底大图存 `assets/branding/corn-bench-plain-corn-1024.png`。
  - 已核对：各尺寸 PNG 生成、透明区与圆形遮罩模拟图人工目检无角标。未在桌面启动器上实机看。
- [x] CI P0：`Install CJK fonts` 不再走 apt（两次 PR run 因 azure 镜像 50 kB/s、单步 20 分钟被 job 超时取消）。
  改为从 GitHub 直下单个 `NotoSansCJKsc-Regular.otf`（Sans2.004，16 MB）+ `actions/cache/restore|save@v5`（只在装成功时存），
  下载 / 安装各自 `timeout-minutes` 且 `continue-on-error`（没字体只影响截图里的中文字形，不影响“能渲染”检查）。
  actions 升到 Node 24 版本：checkout@v5、setup-java@v5、setup-android@v4、setup-gradle@v5、setup-python@v6、upload-artifact@v6。
- [x] 外观迁移 bug（P1-2）：`migrateLegacyAppearance` 改用独立的 `appearance_migrated` 标记，不再拿 `theme_mode` 在不在判断
  （旧数据只有 accent 时会每次冷启动重迁移、覆盖用户后来的强调色）。已有外壳外观值时只补标记不覆盖；读旧值抛异常不记标记、下次再试；
  旧 `AMBER` 显式映射成 `CORN`（`legacyAccentToBench`）。迁移抽成可注入 `DataStore` / `legacy` 的顶层函数。
  - `AppearanceMigrationTest` 重写为 9 个用例，每个用例独立临时 DataStore（不再共用全局单例、去掉 `Thread.sleep`）：
    仅 theme / 仅 accent / AMBER→CORN / 迁移后用户改值不被覆盖 / 已有外壳值不读旧值 / 无旧值也记标记 / 读旧值失败退回默认且不记标记 / Loading / 往返持久化。
  - 反证：临时把判定改回 `contains(theme_mode)`，该测试类 9 个中 3 个红；改回后全绿。
- [x] 外观 Loading（P1-3）：`BenchAppearanceStore.loaded`，首次读盘（含迁移）完成前 `BenchRoot` 只放空的 `bench-loading` 占位、
  不画模式内容（露出跟随系统的窗口底色），读完后第一帧即盘上主题。`BenchAppearanceTest.shellWaitsForStoredAppearanceBeforeDrawing` 锁定。
  `BenchShellTest` 改注入内存外观（`FakeAppearance` 抽到单独文件），不再依赖真 `bench_ui` 读盘时序。
  - 本地：`:app:testDebugUnitTest` 32 个通过（Shell 13、Migration 9、Appearance 5、ModeStore 4、SystemBars 1）。
  - 仍有的残余：用户选了与系统相反的主题时，冷启动的**窗口底色**（Compose 起来之前）仍跟系统走，只是不再画一帧错色内容。
- [x] ModeGuard 单测（P1-4）：两个 Feature 的判定抽成纯函数（`leaveBlockedReasonFor` / `releaseSession`，
  `flashingLeaveBlockedReason` / `releaseFlashingUsb`），对象方法只做转发，行为不变。
  - `ModbusLeaveGuardTest` 7 个：Idle/Error 放行；Running(Demo)、Running(Usb)、RequestingPermission、Opening、Stopping 各自文案；
    容器未创建时放行且不创建容器；空闲不断开；运行中断开并等回到 Idle；Error 视为已释放；卡在 Stopping 超时 false。
  - `FlashingLeaveGuardTest` 7 个：空闲放行；单片烧录优先；PAUSED / FINISHED / WAITING_BOARD / FLASHING 文案；
    钩子先跑；仍被拦截直接 false；等识别结束；识别不结束超时 false。
  - 本地：`:app` 32、`:feature-flashing` 34、`:feature-modbus` 955 个单测全过（0 失败）。
- [x] **redroid 验收发现并修复 P0 崩溃**：从上位机切到烧录模式（或 last_mode=烧录 冷启动）必崩——
  `RuntimeException: Cannot create an instance of class dev.corn.flashing.ui.FlashViewModel`
  ← `NoSuchMethodException: FlashViewModel.<init> []`。原因：外壳每个模式自建的 `ModeViewModelStoreOwner`
  只实现了 `ViewModelStoreOwner`，`viewModel()` 不带工厂时退化成无参构造，而 `FlashViewModel` 是 `AndroidViewModel(Application)`。
  单测里两个模式都用占位内容，所以一直没暴露；redroid 崩溃列表里 10-27 18:06–18:11 的 `dev.corn.bench` 崩溃也是同一处。
  - 修：owner 同时实现 `HasDefaultViewModelProviderFactory`（`AndroidViewModelFactory` + `APPLICATION_KEY`）。
  - 新增 `BenchShellTest.modeContentCanCreateAndroidViewModelsWithoutFactory`；去掉修复时该用例以同样的 `NoSuchMethodException` 红，修复后绿。
  - 本地：`:app:testDebugUnitTest` 33 个通过、`:app:lintDebug` 通过。
- [x] redroid 设备验收（P1-4 后半）：见 `docs/bench-shell.md`「Oracle redroid」一节。构建机无 NDK，验收包来自临时分支
  `tmp/redroid-acceptance`（只多一行 `corn.flashing.skipNative=true`，不合并，验收后删除）。
  上位机演示会话忙 → 烧录卡置灰且不切换；断开后可切；烧录模拟批次忙 / 暂停各自文案正确、不切换；结束批次后可切回；
  last_mode=烧录 冷启动正常；页头各只有一个“切换模式”节点；桌面图标无角标。
  - 注意：redroid 上还装着旧仓的独立 Corn Flashing / Corn Modbus，崩溃后系统会退回旧应用的任务，验收时要看前台包名。
- [x] 文档（P1-5）：`docs/bench-shell.md` 验证清单拆成「本机单测 / redroid / 未验证（真机）」三类。
- 未验证：真机 USB（ST-LINK / RS485 授权、收发、拔插、FLAG_IMMUTABLE 回调）、前后台、TalkBack、真机浅色观感。
- 下一步：P2——切换面板置灰改响应式；外层 `CornTheme` fontScale=1 加断言；烧录页文案“Corn Flashing”改名；Roborazzi 像素基线。

## 2026-10-28 P2 · 分支 feat/p2-shell-polish（基于 main@5d49e51）

- 交接：原定 DeepSeek 做，已叫停；分支上没有它留下的提交或改动（与 main 同 SHA、远端无此分支），从这里接着做。
- 范围：board「下一步：P2」+ 评审 E P2 第 6、7、9、10 条（第 8 条真机专项不在本轮）。不扩协议、不碰真机 USB。
- [x] 切换面板置灰改响应式（P2-6）
  - `ModeGuard` 新增 `blockedReasonFlow(mode)`；`ModeShell` 订阅它（初值仍用同步 `blockedReason`，面板第一帧就对），
    真正切换前仍用 `blockedReason` 重查一次。
  - 两个 Feature 的“已创建容器”从普通字段改成 `MutableStateFlow`，`leaveBlockedReasonFlow()` 在容器没建时恒为 null、
    建了以后自动接上状态，**仍不创建容器**（`ModeStoreTest.defaultGuardQueriesWithoutCreatingContainers` 补了 flow 一路）。
  - 纯函数：`leaveBlockedReasonFlowOf(...)`（上位机）、`flashingLeaveBlockedReasonFlow(running, phase)`（烧录），各 1 个用例
    （状态序列 → 文案序列，重复文案不重发）。
  - `BenchShellTest` +2：面板开着时忙→闲（卡片解除置灰、同一面板内直接能切）、闲→忙→换文案（置灰、文案即时变、点了不切）。
    反证：把 `ModeShell` 改回组合期读一次快照，这 2 个用例红（16 个中 2 个失败），改回后全绿。
