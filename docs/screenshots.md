# 截图测试与像素基线

Roborazzi（Robolectric `GraphicsMode.NATIVE`）截图分两类：

| 类别 | 位置 | 入库 | CI 怎么用 |
| --- | --- | --- | --- |
| **像素基线** | `<模块>/src/test/screenshots/baseline/*.png` | 是 | 逐像素比对，有差异或缺图 → 对应单测失败 → job 红 |
| “能渲染”截图 | `<模块>/src/test/screenshots/` 其它文件 | 否（`.gitignore`） | 只 record，作为 artifact `roborazzi-screenshots` 给人看 |

## 现有基线

| 文件 | 测试 | 内容 |
| --- | --- | --- |
| `feature-modbus/.../baseline/modbus_connection_light_corn.png` | `ShellScreenshotTest.baseline_connection_light_corn` | 上位机连接页，浅色 × 默认玉米 |
| `feature-modbus/.../baseline/modbus_connection_light_blue.png` | `ShellScreenshotTest.baseline_connection_light_blue` | 上位机连接页，浅色 × 蓝 |
| `feature-flashing/.../baseline/flashing_workbench_light_corn.png` | `FlashingBaselineTest.workbench_light_corn` | 烧录工作台（单路、未接探针），浅色 × 玉米 |
| `feature-flashing/.../baseline/flashing_workbench_light_blue.png` | `FlashingBaselineTest.workbench_light_blue` | 同上，浅色 × 蓝 |
| `feature-flashing/.../baseline/flashing_workbench_dark_corn.png` | `FlashingBaselineTest.workbench_dark_corn` | 同上，深色对照 |

尺寸统一 `w360dp-h780dp`、`sdk = 33`。烧录工作台用真实 `FlashViewModel` + `CornApp`（Robolectric 里没有 USB / 原生库，
画面固定为“未就绪 + 连接引导”）；上位机用 `ShellScreenshotTest` 的假会话（空闲、演示设备）。

## 写法

基线截图一律走模块内的 `captureBaseline(name)`（`:feature-modbus` / `:feature-flashing` 各一份 `BaselineCapture.kt`），
不要直接 `captureRoboImage`。它按 Gradle 属性 `corn.baseline` 决定行为：

| `-Pcorn.baseline=` | 行为 |
| --- | --- |
| `verify` | 与入库基线比对：有差异 → `AssertionError ... is changed`；基线缺失 → `The original file ... was not found`。CI 固定用这个。 |
| `record` | 重录基线（覆盖 `baseline/` 下的文件）。 |
| 不传 | 跟随 Roborazzi 任务（`recordRoborazziDebug` 会重录；普通 `testDebugUnitTest` 不截图）。 |

容差：用 Roborazzi 默认比较器（单像素色距 ≤ 0.007，约 1/255 级别的抖动不算差异）。
实测：浅色底色改 1 个色阶不报；把蓝色强调色换成红色，`flashing_workbench_light_blue` 立即失败并生成 `_compare` / `_actual` 图。

## 失败条件（CI）

1. 基线图与本次渲染不同（超出上面的容差）→ 单测失败。对比图与实际图在 artifact **`pixel-baseline-diffs`**
   （`*/build/outputs/roborazzi/*_compare.png` / `*_actual.png`）。
2. 基线文件缺失 → 单测失败（同上，只有 `_actual.png`）。
3. CJK 字体没装上 → `Require CJK font for pixel baselines` 步骤直接失败（不让它退化成一堆“方块字”像素差）。

## 渲染环境与重录

基线依赖字体：中文走系统字体回退，CI 装的是 Noto Sans CJK SC Regular 2.004（`ci.yml` 的 `Install CJK font`）。
**以 CI runner 的渲染为准**；本机字体不同时 `verify` 可能报差异，这不代表 UI 变了。

改了 UI、需要更新基线时：

- 首选：推上去让 CI 比对失败，下载 `pixel-baseline-diffs` 里对应的 `*_actual.png`，改名去掉 `_actual`、覆盖到 `baseline/`，
  确认画面是预期的再提交。下一次 CI 应该是绿的。
- 或（工作流合进 main 之后）：Actions 里手动运行 `ci`，勾 `record_baselines`，下载 artifact **`pixel-baselines`** 覆盖入库。
- 本机：`./gradlew :feature-modbus:testDebugUnitTest :feature-flashing:testDebugUnitTest -Pcorn.baseline=record`，
  只在确认本机字体与 CI 一致时用；提交前用 CI 的比对结果确认。

新增基线：在测试里调 `captureBaseline("<模块>_<页面>_<深浅>_<强调色>")`，按上面的方式录图入库，并在本文“现有基线”表里补一行。
